package org.whispersystems.waveservice.api.push.exceptions;

public class UsernameIsNotAssociatedWithAnAccountException extends NotFoundException {
  public UsernameIsNotAssociatedWithAnAccountException() {
    super("The given username is not associated with an account.");
  }
}
