package dev.nikan.ledgerlite.loan;

public interface CreditScoreClient {

    int fetchScore(String applicantEmail);
}