package xxliam.cookieclient.manager;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标管理器：维护当前战斗目标与好友名单。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.manager.TargetManager}。
 */
public class TargetManager {

    private Entity target;
    private final List<String> friends = new ArrayList<>();

    public Entity getTarget() {
        return target;
    }

    public void setTarget(Entity target) {
        this.target = target;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public void addFriend(String name) {
        if (!isFriend(name)) {
            friends.add(name);
        }
    }

    public void removeFriend(String name) {
        friends.remove(name);
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }
}
