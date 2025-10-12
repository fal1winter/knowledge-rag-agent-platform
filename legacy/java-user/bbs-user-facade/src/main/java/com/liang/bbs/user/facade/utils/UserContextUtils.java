package com.liang.bbs.user.facade.utils;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import lombok.Data;

/**
 */

@Data
public class UserContextUtils {
    private static ThreadLocal<UserSsoDTO> threadLocal = new TransmittableThreadLocal();

    public UserContextUtils() {
    }

    public static void removeCurrentUser() {
        threadLocal.remove();
    }

    public static void setCurrentUser(UserSsoDTO value) {
        threadLocal.set(value);
    }

    public static UserSsoDTO currentUser() {
        return threadLocal.get();
    }
}
