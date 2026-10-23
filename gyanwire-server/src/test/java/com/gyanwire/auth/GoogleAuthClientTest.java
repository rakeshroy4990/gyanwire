package com.gyanwire.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAuthClientTest {

    private GoogleAuthClient client;

    @BeforeEach
    void setUp() {
        client = new GoogleAuthClient(new ObjectMapper());
    }

    @Test
    void parseUserInfoRequiresVerifiedEmail() throws Exception {
        assertThat(client.parseUserInfo("""
                {"email":"a@example.com","email_verified":false,"given_name":"A"}
                """)).isNull();
    }

    @Test
    void parseUserInfoAcceptsVerifiedEmailString() throws Exception {
        GoogleAuthClient.GoogleProfile profile = client.parseUserInfo("""
                {"email":"A@Example.COM","email_verified":"true","given_name":"Ada","family_name":"Lovelace","picture":"https://lh3.googleusercontent.com/a"}
                """);
        assertThat(profile).isNotNull();
        assertThat(profile.email()).isEqualTo("a@example.com");
        assertThat(profile.givenName()).isEqualTo("Ada");
        assertThat(profile.familyName()).isEqualTo("Lovelace");
        assertThat(profile.picture()).contains("googleusercontent");
    }

    @Test
    void parseUserInfoSplitsFullNameWhenGivenMissing() throws Exception {
        GoogleAuthClient.GoogleProfile profile = client.parseUserInfo("""
                {"email":"b@example.com","verified_email":true,"name":"Grace Hopper"}
                """);
        assertThat(profile.givenName()).isEqualTo("Grace");
        assertThat(profile.familyName()).isEqualTo("Hopper");
    }

    @Test
    void parseUserInfoRejectsBlankEmail() throws Exception {
        assertThat(client.parseUserInfo("""
                {"email":" ","email_verified":true}
                """)).isNull();
    }

    @Test
    void fetchVerifiedProfileReturnsNullForBlankToken() {
        assertThat(client.fetchVerifiedProfile(" ")).isNull();
        assertThat(client.fetchVerifiedProfile(null)).isNull();
    }

    @Test
    void fetchVerifiedProfileReturnsNullWhenFetcherFails() {
        client.useFetcherForTest(token -> null);
        assertThat(client.fetchVerifiedProfile("tok")).isNull();
    }

    @Test
    void fetchVerifiedProfileParsesSuccessfulUserInfo() {
        client.useFetcherForTest(token -> """
                {"email":"ok@example.com","email_verified":true,"given_name":"Ok","family_name":"User"}
                """);
        GoogleAuthClient.GoogleProfile profile = client.fetchVerifiedProfile("ya29.token");
        assertThat(profile).isNotNull();
        assertThat(profile.email()).isEqualTo("ok@example.com");
        assertThat(profile.givenName()).isEqualTo("Ok");
    }
}
