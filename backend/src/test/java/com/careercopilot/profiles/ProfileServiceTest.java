package com.careercopilot.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profiles;

    @InjectMocks
    private ProfileService service;

    @Test
    void getThrowsWhenTheUserHasNoProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(1L)).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void getReturnsTheExistingProfile() {
        Profile existing = new Profile(1L);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getProfile(1L)).isSameAs(existing);
    }

    @Test
    void upsertCreatesAProfileWhenNoneExists() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        when(profiles.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile result = service.upsertProfile(1L,
                new ProfileRequest("  Jane Doe ", " Engineer ", " Berlin ", true, false));

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getFullName()).isEqualTo("Jane Doe");
        assertThat(result.getHeadline()).isEqualTo("Engineer");
        assertThat(result.getLocation()).isEqualTo("Berlin");
        assertThat(result.isRemoteOk()).isTrue();
        assertThat(result.isAutoSubmitEnabled()).isFalse();
    }

    @Test
    void upsertUpdatesTheExistingProfileWithoutCreatingANewOne() {
        Profile existing = new Profile(1L);
        existing.setFullName("Old Name");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(profiles.save(existing)).thenReturn(existing);

        Profile result = service.upsertProfile(1L, new ProfileRequest("New Name", null, null, false, true));

        assertThat(result).isSameAs(existing);
        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(existing.isAutoSubmitEnabled()).isTrue();
        verify(profiles, times(1)).save(any(Profile.class));
        verify(profiles).save(existing);
    }

    @Test
    void blankHeadlineAndLocationAreStoredAsNull() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        when(profiles.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile result = service.upsertProfile(1L, new ProfileRequest("Jane", "   ", "", false, false));

        assertThat(result.getHeadline()).isNull();
        assertThat(result.getLocation()).isNull();
    }
}
