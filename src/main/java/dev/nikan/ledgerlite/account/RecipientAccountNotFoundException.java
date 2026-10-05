package dev.nikan.ledgerlite.account;

public class RecipientAccountNotFoundException extends RuntimeException {

  public RecipientAccountNotFoundException(String accountNumber) {
    super("Recipient account not found: " + accountNumber);
  }
}