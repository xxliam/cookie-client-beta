package info.u_team.oauth_account_manager.util;

import net.hycrafthd.minecraft_authenticator.login.User;
import net.hycrafthd.minecraft_authenticator.login.XBoxProfile;

public record LoadedAccount(User user, XBoxProfile xboxProfile) {
}
