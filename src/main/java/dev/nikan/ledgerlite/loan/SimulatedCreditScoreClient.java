package dev.nikan.ledgerlite.loan;

import org.springframework.stereotype.Component;

@Component
public class SimulatedCreditScoreClient implements CreditScoreClient {

    @Override
    public int fetchScore(String applicantEmail) {
        int base = 600;
        int variation = Math.floorMod(applicantEmail.hashCode(), 201);
        return base + variation;
    }
}