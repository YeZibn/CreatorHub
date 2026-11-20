package com.creatorhub.core.identity.service;

import java.util.Locale;
import java.util.Optional;

import com.creatorhub.core.identity.entity.NewAccount;
import com.creatorhub.core.identity.entity.UserAccount;
import com.creatorhub.core.identity.mapper.UserAccountMapper;
import com.creatorhub.core.identity.mapper.UserProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserAccountMapper accounts;
    private final UserProfileMapper profiles;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AccountService(UserAccountMapper accounts, UserProfileMapper profiles, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.passwords = passwords;
        this.dummyHash = passwords.encode("constant used for timing parity");
    }

    public Optional<Long> authenticate(String normalizedEmail, String password) {
        UserAccount account = accounts.selectByEmail(normalizedEmail);
        boolean matches = passwords.matches(password, account == null ? dummyHash : account.passwordHash());
        return matches && account != null && "ACTIVE".equals(account.status())
                ? Optional.of(account.id()) : Optional.empty();
    }

    public String emailOf(long userId) {
        return accounts.selectEmailById(userId);
    }

    @Transactional
    public long register(String normalizedEmail, String password, String displayName) {
        NewAccount account = new NewAccount(normalizedEmail, passwords.encode(password));
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException ex) {
            if (ex.getMostSpecificCause().getMessage() != null
                    && ex.getMostSpecificCause().getMessage().contains("uk_user_account_email")) {
                throw new EmailAlreadyRegisteredException(ex);
            }
            throw ex;
        }
        profiles.insert(account.getId(), displayName);
        return account.getId();
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.strip();
        return trimmed.chars().allMatch(character -> character < 128)
                ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }
}
