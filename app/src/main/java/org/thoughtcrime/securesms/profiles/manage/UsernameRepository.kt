package org.thoughtcrime.securesms.profiles.manage

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.wave.core.models.ServiceId.ACI
import org.wave.core.util.Base64
import org.wave.core.util.Result
import org.wave.core.util.Result.Companion.failure
import org.wave.core.util.Result.Companion.success
import org.wave.core.util.UuidUtil
import org.wave.core.util.logging.Log
import org.wave.core.util.toByteArray
import org.wave.libwave.net.RequestResult
import org.wave.libwave.usernames.BaseUsernameException
import org.wave.libwave.usernames.Username
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkResetResult
import org.thoughtcrime.securesms.database.WaveDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.WaveStore
import org.thoughtcrime.securesms.net.WaveNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.UsernameUtil
import org.whispersystems.waveservice.api.NetworkResult
import org.whispersystems.waveservice.api.WaveServiceAccountManager
import org.whispersystems.waveservice.api.push.UsernameLinkComponents
import org.whispersystems.waveservice.api.util.Usernames
import java.util.UUID

/**
 * Performs various actions around usernames and username links.
 *
 * Usernames and username links are more complicated than you may think. This is because we want the following properties:
 * - We want usernames to be assigned a random numerical discriminator to avoid land grabs
 * - We don't want to store plaintext usernames on the service
 * - We don't want plaintext usernames in username links
 * - We want username links to be revocable and rotatable without changing your username
 * - We want users to be able to turn a link into a displayable username in the app
 *
 * As a result, the process of reserving them, creating links, and parsing those links is more complex.
 *
 * # Setting a username
 *
 * To start, let's define a username as being composed of two parts: a nickname and a discriminator. The nickname is the user-chosen part of the username, and
 * the discriminator is a random set of digits that we bolt onto the end so that people can choose whatever nickname they want. So a username ends up looking
 * like this: mynickname.123
 *
 * Setting a username is a multi-step process.
 *
 * 1. The user chooses a nickname.
 * 2. We take that nickname and pair it with a bunch of possible discriminators of different lengths, turning them into a list of possible usernames.
 * 3. We hash those possible usernames and submit them to the service. It will reserve the first one that's available, returning it in the response.
 * 4. We present the (nickname, discriminator) combo to the user, and they can choose to confirm it.
 * 5. If the user confirms it, we tell the service the final username hash, and it saves it as the final username.
 *
 * # Username links
 *
 * There's three main components to username links:
 * - An encrypted username blob
 * - A serverId (which is a UUID)
 * - "entropy" (some random bytes used to encrypt the username)
 *
 * The service basically stores a map of (serverId -> encrypted username blob). We can ask the service for the encrypted username blob for a given serverId,
 * and then decrypt it with the entropy. Simple enough.
 *
 * How are those pieces shared? Well, the link looks like this:
 * https://wave.me/#eu/<32 bytes of entropy><16 bytes of serverId uuid>
 *
 * So, when we get a link, we parse out the entropy and serverId. We then use the serverId to get the encrypted username, and then decrypt it with the entropy.
 *
 * This gives us everything we want:
 * - We can rotate our link without changing our username by just picking new (serverId, entropy) and storing a new blob on the service.
 * - When the user decrypts the username, they see it displayed exactly how the user uploaded it.
 * - The service has no idea what links correspond to what usernames -- it's just storing encrypted blobs.
 */
object UsernameRepository {
  private val TAG = Log.tag(UsernameRepository::class.java)

  private val URL_REGEX = """(https://)?wave.me/?#eu/([a-zA-Z0-9+\-_/]+)""".toRegex()

  private const val BASE_URL = "https://wave.me/#eu/"
  private const val USERNAME_SYNC_ERROR_THRESHOLD = 3

  private val accountManager: WaveServiceAccountManager get() = AppDependencies.waveServiceAccountManager

  /**
   * Given a nickname, this will temporarily reserve a matching discriminator that can later be confirmed via [confirmUsernameAndCreateNewLink].
   */
  fun reserveUsername(nickname: String, discriminator: String?): Single<Result<UsernameState.Reserved, UsernameSetResult>> {
    return Single
      .fromCallable { reserveUsernameInternal(nickname, discriminator) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * This changes the encrypted username associated with your current username link.
   * The intent of this is to allow users to change the casing of their username without changing the link,
   * since usernames are case-insensitive.
   */
  fun updateUsernameDisplayForCurrentLink(updatedUsername: Username): Single<UsernameSetResult> {
    return Single
      .fromCallable { updateUsernameDisplayForCurrentLinkInternal(updatedUsername) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a reserved username (obtained via [reserveUsername]), this will confirm that reservation, assigning the user that username.
   * It will also create a new username link. Therefore, be sure to call [updateUsernameDisplayForCurrentLink] instead if all that has changed is the
   * casing, and you want to keep the link the same.
   */
  fun confirmUsernameAndCreateNewLink(username: Username): Single<UsernameSetResult> {
    return Single
      .fromCallable { confirmUsernameAndCreateNewLinkInternal(username) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Attempts to reclaim the username that is currently stored on disk if necessary.
   * This is intended to be used after registration.
   *
   * This method call may result in mutating [WaveStore] state.
   */
  @WorkerThread
  @JvmStatic
  fun reclaimUsernameIfNecessary(): UsernameReclaimResult {
    if (!WaveStore.misc.needsUsernameRestore) {
      Log.d(TAG, "[reclaimUsernameIfNecessary] No need to restore username. Skipping.")
      return UsernameReclaimResult.SUCCESS
    }

    val username = WaveStore.account.username
    val link = WaveStore.account.usernameLink

    if (username == null || link == null) {
      Log.d(TAG, "[reclaimUsernameIfNecessary] No username or link to restore. Skipping.")
      WaveStore.misc.needsUsernameRestore = false
      return UsernameReclaimResult.SUCCESS
    }

    val result = reclaimUsernameIfNecessaryInternal(Username(username), link)

    when (result) {
      UsernameReclaimResult.SUCCESS -> {
        Log.i(TAG, "[reclaimUsernameIfNecessary] Successfully reclaimed username and link.")
        WaveStore.misc.needsUsernameRestore = false
      }

      UsernameReclaimResult.PERMANENT_ERROR -> {
        Log.w(TAG, "[reclaimUsernameIfNecessary] Permanently failed to reclaim username and link. User will see an error.")
        WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED
        WaveStore.misc.needsUsernameRestore = false
      }

      UsernameReclaimResult.NETWORK_ERROR -> {
        Log.w(TAG, "[reclaimUsernameIfNecessary] Hit a transient network error while trying to reclaim username and link.")
      }
    }

    return result
  }

  /**
   * Deletes the username from the local user's account
   */
  @JvmStatic
  fun deleteUsernameAndLink(): Single<UsernameDeleteResult> {
    return Single
      .fromCallable { deleteUsernameInternal() }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Creates or rotates the username link for the local user.
   */
  fun createOrResetUsernameLink(): Single<UsernameLinkResetResult> {
    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[createOrResetUsernameLink] No network! Not making any changes.")
      return Single.just(UsernameLinkResetResult.NetworkUnavailable)
    }

    val usernameString = WaveStore.account.username
    if (usernameString.isNullOrBlank()) {
      Log.w(TAG, "[createOrResetUsernameLink] No username set! Cannot rotate the link!")
      return Single.just(UsernameLinkResetResult.UnexpectedError)
    }

    val username = try {
      Username(usernameString)
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[createOrResetUsernameLink] Failed to parse our own username! Cannot rotate the link!")
      return Single.just(UsernameLinkResetResult.UnexpectedError)
    }

    return Single
      .fromCallable {
        WaveStore.account.usernameLink = null

        Log.d(TAG, "[createOrResetUsernameLink] Creating username link...")

        val usernameLink = username.generateLink()
        when (val result = WaveNetwork.account.createUsernameLink(usernameLink)) {
          is NetworkResult.Success -> {
            WaveStore.account.usernameLink = result.result

            if (WaveStore.account.usernameSyncState == AccountValues.UsernameSyncState.LINK_CORRUPTED) {
              WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
              WaveStore.account.usernameSyncErrorCount = 0
            }

            WaveDatabase.recipients.markNeedsSync(Recipient.self().id)
            StorageSyncHelper.scheduleSyncForDataChange()
            Log.d(TAG, "[createOrResetUsernameLink] Username link created.")

            UsernameLinkResetResult.Success(result.result)
          }
          else -> {
            Log.w(TAG, "[createOrResetUsernameLink] Failed to rotate the username!", result.getCause())
            UsernameLinkResetResult.NetworkError
          }
        }
      }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a full username link, this will do the necessary parsing and network lookups to resolve it to a (username, ACI) pair.
   */
  @JvmStatic
  fun fetchUsernameAndAciFromLink(url: String): Single<UsernameLinkConversionResult> {
    val components: UsernameLinkComponents = parseLink(url) ?: return Single.just(UsernameLinkConversionResult.Invalid)

    return Single
      .fromCallable {
        val encryptedUsername = when (val result = WaveNetwork.username.getEncryptedUsernameFromLinkServerId(components.serverId)) {
          is NetworkResult.Success -> result.result
          is NetworkResult.StatusCodeError -> {
            return@fromCallable when (result.code) {
              404 -> UsernameLinkConversionResult.NotFound(null)
              422 -> UsernameLinkConversionResult.Invalid
              else -> UsernameLinkConversionResult.NetworkError
            }
          }
          is NetworkResult.NetworkError -> return@fromCallable UsernameLinkConversionResult.NetworkError
          is NetworkResult.ApplicationError -> throw result.throwable
        }

        val link = Username.UsernameLink(components.entropy, encryptedUsername)
        val username: Username = try {
          Username.fromLink(link)
        } catch (e: BaseUsernameException) {
          Log.w(TAG, "[convertLinkToUsername] Bad username conversion.", e)
          return@fromCallable UsernameLinkConversionResult.Invalid
        }

        when (val result = WaveNetwork.username.getAciByUsername(username)) {
          is RequestResult.Success -> {
            result.result?.let {
              UsernameLinkConversionResult.Success(username, it)
            } ?: UsernameLinkConversionResult.NotFound(username)
          }
          is RequestResult.RetryableNetworkError -> {
            UsernameLinkConversionResult.NetworkError
          }
          is RequestResult.NonSuccess -> {
            throw AssertionError()
          }
          is RequestResult.ApplicationError -> throw result.cause
        }
      }
      .subscribeOn(Schedulers.io())
  }

  @JvmStatic
  fun fetchAciForUsername(usernameString: String): UsernameAciFetchResult {
    val username = try {
      Username(usernameString)
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[fetchAciFromUsername] Invalid username", e)
      return UsernameAciFetchResult.NotFound
    }

    return when (val result = WaveNetwork.username.getAciByUsername(username)) {
      is RequestResult.Success -> {
        result.result?.let {
          UsernameAciFetchResult.Success(it)
        } ?: UsernameAciFetchResult.NotFound
      }
      is RequestResult.NonSuccess -> {
        throw AssertionError()
      }
      is RequestResult.RetryableNetworkError -> {
        UsernameAciFetchResult.NetworkError
      }
      is RequestResult.ApplicationError -> throw result.cause
    }
  }

  /**
   * Parses out the [UsernameLinkComponents] from a link if possible, otherwise null.
   * You need to make a separate network request to convert these components into a username.
   */
  @JvmStatic
  fun parseLink(url: String): UsernameLinkComponents? {
    val match: MatchResult = URL_REGEX.find(url) ?: return null
    val path: String = match.groups[2]?.value ?: return null
    val allBytes: ByteArray = Base64.decode(path)

    if (allBytes.size != 48) {
      return null
    }

    val entropy: ByteArray = allBytes.slice(0 until 32).toByteArray()
    val serverId: ByteArray = allBytes.slice(32 until allBytes.size).toByteArray()
    val serverIdUuid: UUID = UuidUtil.parseOrNull(serverId) ?: return null

    return UsernameLinkComponents(entropy = entropy, serverId = serverIdUuid)
  }

  fun UsernameLinkComponents.toLink(): String {
    val combined: ByteArray = this.entropy + this.serverId.toByteArray()
    val base64 = Base64.encodeUrlSafeWithoutPadding(combined)
    return BASE_URL + base64
  }

  fun isValidLink(url: String): Boolean {
    return parseLink(url) != null
  }

  @JvmStatic
  fun onUsernameConsistencyValidated() {
    WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC

    if (WaveStore.account.usernameSyncErrorCount > 0) {
      Log.i(TAG, "Username consistency validated. There were previously ${WaveStore.account.usernameSyncErrorCount} error(s).")
      WaveStore.account.usernameSyncErrorCount = 0
    }
  }

  @JvmStatic
  fun onUsernameMismatchDetected() {
    WaveStore.account.usernameSyncErrorCount++

    if (WaveStore.account.usernameSyncErrorCount >= USERNAME_SYNC_ERROR_THRESHOLD) {
      Log.w(TAG, "We've now seen ${WaveStore.account.usernameSyncErrorCount} mismatches in a row. Marking username and link as corrupted.")
      WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED
      WaveStore.account.usernameSyncErrorCount = 0
    } else {
      Log.w(TAG, "Username mismatch reported. At ${WaveStore.account.usernameSyncErrorCount} / $USERNAME_SYNC_ERROR_THRESHOLD tries.")
    }
  }

  @JvmStatic
  fun onUsernameLinkMismatchDetected() {
    WaveStore.account.usernameSyncErrorCount++

    if (WaveStore.account.usernameSyncErrorCount >= USERNAME_SYNC_ERROR_THRESHOLD) {
      Log.w(TAG, "We've now seen ${WaveStore.account.usernameSyncErrorCount} mismatches in a row. Marking link as corrupted.")
      WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.LINK_CORRUPTED
      WaveStore.account.usernameLink = null
      WaveStore.account.usernameSyncErrorCount = 0
      StorageSyncHelper.scheduleSyncForDataChange()
    } else {
      Log.w(TAG, "Link mismatch reported. At ${WaveStore.account.usernameSyncErrorCount} / $USERNAME_SYNC_ERROR_THRESHOLD tries.")
    }
  }

  @WorkerThread
  private fun reserveUsernameInternal(nickname: String, discriminator: String?): Result<UsernameState.Reserved, UsernameSetResult> {
    val candidates: List<Username> = try {
      if (discriminator == null) {
        Username.candidatesFrom(nickname, UsernameUtil.MIN_NICKNAME_LENGTH, UsernameUtil.MAX_NICKNAME_LENGTH)
      } else {
        listOf(Username("$nickname${Usernames.DELIMITER}$discriminator"))
      }
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[reserveUsername] An error occurred while generating candidates.")
      return failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR)
    }

    val hashes: List<String> = candidates
      .map { Base64.encodeUrlSafeWithoutPadding(it.hash) }

    return when (val result = WaveNetwork.account.reserveUsername(hashes)) {
      is NetworkResult.Success -> {
        val hashIndex = hashes.indexOf(result.result.usernameHash)
        if (hashIndex == -1) {
          Log.w(TAG, "[reserveUsername] The response hash could not be found in our set of hashes.")
          return failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR)
        }

        Log.i(TAG, "[reserveUsername] Successfully reserved username.")
        success(UsernameState.Reserved(candidates[hashIndex]))
      }
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          409 -> {
            Log.w(TAG, "[reserveUsername] Username taken.")
            failure(UsernameSetResult.USERNAME_UNAVAILABLE)
          }
          422 -> {
            Log.w(TAG, "[reserveUsername] Username malformed.")
            failure(UsernameSetResult.USERNAME_INVALID)
          }
          429 -> {
            Log.w(TAG, "[reserveUsername] Rate limit exceeded.")
            failure(UsernameSetResult.RATE_LIMIT_ERROR)
          }
          else -> {
            Log.w(TAG, "[reserveUsername] Generic network exception.", result.exception)
            failure(UsernameSetResult.NETWORK_ERROR)
          }
        }
      }
      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[reserveUsername] Generic network exception.", result.exception)
        failure(UsernameSetResult.NETWORK_ERROR)
      }
      is NetworkResult.ApplicationError -> throw result.throwable
    }
  }

  @WorkerThread
  private fun updateUsernameDisplayForCurrentLinkInternal(updatedUsername: Username): UsernameSetResult {
    Log.i(TAG, "[updateUsernameDisplayForCurrentLink] Beginning username update...")

    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[deleteUsernameInternal] No network connection! Not attempting the request.")
      return UsernameSetResult.NETWORK_ERROR
    }

    val oldUsernameLink = WaveStore.account.usernameLink ?: return UsernameSetResult.USERNAME_INVALID
    val newUsernameLink = updatedUsername.generateLink(oldUsernameLink.entropy)

    return when (val result = WaveNetwork.account.updateUsernameLink(newUsernameLink)) {
      is NetworkResult.Success -> {
        WaveStore.account.username = updatedUsername.username
        WaveStore.account.usernameLink = result.result
        WaveDatabase.recipients.setUsername(Recipient.self().id, updatedUsername.username)
        WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
        WaveStore.account.usernameSyncErrorCount = 0

        WaveDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
        Log.i(TAG, "[updateUsernameDisplayForCurrentLink] Successfully updated username.")

        UsernameSetResult.SUCCESS
      }
      else -> {
        Log.w(TAG, "[updateUsernameDisplayForCurrentLink] Generic network exception.", result.getCause())
        UsernameSetResult.NETWORK_ERROR
      }
    }
  }

  @WorkerThread
  private fun confirmUsernameAndCreateNewLinkInternal(username: Username): UsernameSetResult {
    Log.i(TAG, "[confirmUsernameAndCreateNewLink] Beginning username confirmation...")

    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[confirmUsernameAndCreateNewLink] No network connection! Not attempting the request.")
      return UsernameSetResult.NETWORK_ERROR
    }

    val link = username.generateLink()

    return when (val result = WaveNetwork.account.confirmUsername(username, link)) {
      is NetworkResult.Success -> {
        WaveStore.account.username = username.username
        WaveStore.account.usernameLink = UsernameLinkComponents(link.entropy, result.result)
        WaveDatabase.recipients.setUsername(Recipient.self().id, username.username)
        WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
        WaveStore.account.usernameSyncErrorCount = 0

        WaveDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
        Log.i(TAG, "[confirmUsernameAndCreateNewLink] Successfully confirmed username.")

        UsernameSetResult.SUCCESS
      }

      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          409 -> {
            Log.w(TAG, "[confirmUsernameAndCreateNewLink] Username was not reserved.")
            UsernameSetResult.USERNAME_INVALID
          }

          410 -> {
            Log.w(TAG, "[confirmUsernameAndCreateNewLink] Username gone.")
            UsernameSetResult.USERNAME_UNAVAILABLE
          }

          else -> {
            Log.w(TAG, "[confirmUsernameAndCreateNewLink] Generic network exception.", result.exception)
            UsernameSetResult.NETWORK_ERROR
          }
        }
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[confirmUsernameAndCreateNewLink] Generic network exception.", result.exception)
        UsernameSetResult.NETWORK_ERROR
      }

      is NetworkResult.ApplicationError -> {
        if (result.throwable is BaseUsernameException) {
          Log.w(TAG, "[confirmUsernameAndCreateNewLink] Username was not reserved.")
          UsernameSetResult.USERNAME_INVALID
        } else {
          throw result.throwable
        }
      }
    }
  }

  @WorkerThread
  private fun deleteUsernameInternal(): UsernameDeleteResult {
    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[deleteUsernameInternal] No network connection! Not attempting the request.")
      return UsernameDeleteResult.NETWORK_ERROR
    }

    return when (val result = WaveNetwork.account.deleteUsername()) {
      is NetworkResult.Success -> {
        WaveDatabase.recipients.setUsername(Recipient.self().id, null)
        WaveStore.account.username = null
        WaveStore.account.usernameLink = null
        WaveStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
        WaveStore.account.usernameSyncErrorCount = 0
        WaveDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
        Log.i(TAG, "[deleteUsername] Successfully deleted the username.")
        UsernameDeleteResult.SUCCESS
      }
      else -> {
        Log.w(TAG, "[deleteUsername] Generic network exception.", result.getCause())
        UsernameDeleteResult.NETWORK_ERROR
      }
    }
  }

  @WorkerThread
  @JvmStatic
  private fun reclaimUsernameIfNecessaryInternal(username: Username, usernameLinkComponents: UsernameLinkComponents): UsernameReclaimResult {
    val link = username.generateLink(usernameLinkComponents.entropy)

    return when (val result = WaveNetwork.account.confirmUsername(username, link)) {
      is NetworkResult.Success -> UsernameReclaimResult.SUCCESS
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          409 -> {
            Log.w(TAG, "[reclaimUsername] Username was not reserved.")
            UsernameReclaimResult.PERMANENT_ERROR
          }

          410 -> {
            Log.w(TAG, "[reclaimUsername] Username gone.")
            UsernameReclaimResult.PERMANENT_ERROR
          }

          else -> {
            Log.w(TAG, "[reclaimUsername] Network error.", result.exception)
            UsernameReclaimResult.NETWORK_ERROR
          }
        }
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[reclaimUsername] Network error.", result.exception)
        UsernameReclaimResult.NETWORK_ERROR
      }

      is NetworkResult.ApplicationError -> {
        if (result.throwable is BaseUsernameException) {
          Log.w(TAG, "[reclaimUsername] Invalid username.")
          UsernameReclaimResult.PERMANENT_ERROR
        } else {
          throw result.throwable
        }
      }
    }
  }

  enum class UsernameSetResult {
    SUCCESS,
    USERNAME_UNAVAILABLE,
    USERNAME_INVALID,
    NETWORK_ERROR,
    CANDIDATE_GENERATION_ERROR,
    RATE_LIMIT_ERROR
  }

  enum class UsernameReclaimResult {
    SUCCESS,
    PERMANENT_ERROR,
    NETWORK_ERROR
  }

  enum class UsernameDeleteResult {
    SUCCESS,
    NETWORK_ERROR
  }

  internal interface Callback<E> {
    fun onComplete(result: E)
  }

  sealed class UsernameLinkConversionResult {
    /** Successfully converted. Contains the username. */
    data class Success(val username: Username, val aci: ACI) : UsernameLinkConversionResult()

    /** Failed to convert due to a network error. */
    object NetworkError : UsernameLinkConversionResult()

    /** Failed to convert because the link or contents were invalid. */
    object Invalid : UsernameLinkConversionResult()

    /** No user exists for the given link. */
    data class NotFound(val username: Username?) : UsernameLinkConversionResult()
  }

  sealed class UsernameAciFetchResult {
    class Success(val aci: ACI) : UsernameAciFetchResult()
    object NotFound : UsernameAciFetchResult()
    object NetworkError : UsernameAciFetchResult()
  }
}
