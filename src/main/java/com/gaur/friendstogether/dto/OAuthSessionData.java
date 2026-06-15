package com.gaur.friendstogether.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuthSessionData implements Serializable {
    private String email;
    private String username;
    private String providerId;
    private String provider;
}
