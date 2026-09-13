package com.wattwise.repository;

import com.wattwise.model.entity.PushToken;
import com.wattwise.model.entity.User;
import com.wattwise.model.enums.Platform;
import com.wattwise.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PushTokenRepositoryTest {

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user(Long id) {
        User user = new User();
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("hash");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private PushToken token(String raw, User user, Platform platform) {
        PushToken pushToken = new PushToken();
        pushToken.setToken(raw);
        pushToken.setUser(user);
        pushToken.setPlatform(platform);
        return pushToken;
    }

    @Test
    void savesAndFindsTokensByUserId() {
        User alice = user(1L);
        pushTokenRepository.save(token("fcm-1", alice, Platform.ANDROID));
        pushTokenRepository.save(token("fcm-2", alice, Platform.ANDROID));

        List<PushToken> found = pushTokenRepository.findByUserId(alice.getId());

        assertThat(found).hasSize(2);
        assertThat(found.stream().map(PushToken::getToken)).containsExactlyInAnyOrder("fcm-1", "fcm-2");
        assertThat(pushTokenRepository.countByUserId(alice.getId())).isEqualTo(2L);
    }

    @Test
    void findFirstByTokenAndExistsDetectRegisteredTokens() {
        User alice = user(1L);
        pushTokenRepository.save(token("fcm-1", alice, Platform.ANDROID));

        Optional<PushToken> found = pushTokenRepository.findFirstByToken("fcm-1");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(alice.getId());
        assertThat(pushTokenRepository.existsByToken("fcm-1")).isTrue();
        assertThat(pushTokenRepository.existsByToken("nope")).isFalse();
    }

    @Test
    void deleteByTokenRemovesOnlyThatToken() {
        User alice = user(1L);
        pushTokenRepository.save(token("fcm-1", alice, Platform.ANDROID));
        pushTokenRepository.save(token("fcm-2", alice, Platform.ANDROID));

        pushTokenRepository.deleteByToken("fcm-1");

        assertThat(pushTokenRepository.findByUserId(alice.getId())).hasSize(1);
        assertThat(pushTokenRepository.countByUserId(alice.getId())).isEqualTo(1L);
    }

    @Test
    void deleteByUserIdRemovesAllTokensOfUser() {
        User alice = user(1L);
        pushTokenRepository.save(token("fcm-1", alice, Platform.ANDROID));
        pushTokenRepository.save(token("fcm-2", alice, Platform.ANDROID));

        pushTokenRepository.deleteByUserId(alice.getId());

        assertThat(pushTokenRepository.countByUserId(alice.getId())).isZero();
    }
}