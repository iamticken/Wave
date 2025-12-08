package org.whispersystems.waveservice.api.messages.multidevice;


import org.whispersystems.waveservice.api.messages.WaveServiceAttachment;

public class ContactsMessage {

  private final WaveServiceAttachment contacts;
  private final boolean                 complete;

  public ContactsMessage(WaveServiceAttachment contacts, boolean complete) {
    this.contacts = contacts;
    this.complete = complete;
  }

  public WaveServiceAttachment getContactsStream() {
    return contacts;
  }

  public boolean isComplete() {
    return complete;
  }
}
