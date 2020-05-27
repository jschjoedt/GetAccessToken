package dk.radius.java.modules.pojo;

import com.google.gson.annotations.SerializedName;

public class DO_AccessToken {
	@SerializedName("access_token")
	public String accessToken;
	@SerializedName("refresh_token")
	public String refreshToken;
	@SerializedName("expires_in")
	public String expiresIn;
	@SerializedName("refresh_token_expires_in")
	public String refreshTokenExpiresIn;

	
}
