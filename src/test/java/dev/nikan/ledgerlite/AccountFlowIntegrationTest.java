package dev.nikan.ledgerlite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountFlowIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mvc;

    @Test
    void registerLoginAndReadCurrentUser() throws Exception {
        String email = newEmail();
        register(email);

        String token = login(email);

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void depositThenTransferMovesMoneyExactlyOnce() throws Exception {
        String token = registerAndLogin(newEmail());
        String sourceId = openAccount(token, "CHECKING");
        String sourceNumber = accountNumber(token, sourceId);
        String targetId = openAccount(token, "SAVINGS");
        String targetNumber = accountNumber(token, targetId);

        deposit(token, sourceId, "500.00");

        String key = UUID.randomUUID().toString();
        String firstTransferId = transfer(token, sourceId, targetNumber, "100.00", key);
        String replayedTransferId = transfer(token, sourceId, targetNumber, "100.00", key);

        assertThat(replayedTransferId).isEqualTo(firstTransferId);
        assertThat(balance(token, sourceId)).isEqualByComparingTo("400.00");
        assertThat(balance(token, targetId)).isEqualByComparingTo("100.00");
        assertThat(sourceNumber).isNotEqualTo(targetNumber);
    }

    @Test
    void transferBeyondBalanceIsRejected() throws Exception {
        String token = registerAndLogin(newEmail());
        String sourceId = openAccount(token, "CHECKING");
        String targetId = openAccount(token, "SAVINGS");
        String targetNumber = accountNumber(token, targetId);

        mvc.perform(post("/accounts/" + sourceId + "/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccountNumber\":\"" + targetNumber + "\",\"amount\":10.00}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void userCannotReadAnotherUsersAccount() throws Exception {
        String ownerToken = registerAndLogin(newEmail());
        String accountId = openAccount(ownerToken, "CHECKING");

        String otherToken = registerAndLogin(newEmail());

        mvc.perform(get("/accounts/" + accountId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private String newEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndLogin(String email) throws Exception {
        register(email);
        return login(email);
    }

    private String openAccount(String token, String type) throws Exception {
        mvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated());

        return accountIdOfLastOpened(token);
    }

    private String accountIdOfLastOpened(String token) throws Exception {
        JsonNode accounts = listAccounts(token);
        return accounts.get(accounts.size() - 1).get("id").asText();
    }

    private String accountNumber(String token, String accountId) throws Exception {
        return findAccount(token, accountId).get("accountNumber").asText();
    }

    private BigDecimal balance(String token, String accountId) throws Exception {
        return findAccount(token, accountId).get("balance").decimalValue();
    }

    private void deposit(String token, String accountId, String amount) throws Exception {
        mvc.perform(post("/accounts/" + accountId + "/deposits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private String transfer(String token, String fromId, String toNumber, String amount, String key)
            throws Exception {
        MvcResult result = mvc.perform(post("/accounts/" + fromId + "/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccountNumber\":\"" + toNumber + "\",\"amount\":" + amount + "}"))
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode listAccounts(String token) throws Exception {
        MvcResult result = mvc.perform(get("/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findAccount(String token, String accountId) throws Exception {
        JsonNode accounts = listAccounts(token);
        for (JsonNode account : accounts) {
            if (account.get("id").asText().equals(accountId)) {
                return account;
            }
        }
        throw new AssertionError("account not found: " + accountId);
    }
}
