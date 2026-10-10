package com.offertracker.common;

public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();
    private CurrentUserContext() {}
    public static void set(CurrentUser user) { CURRENT.set(user); }
    public static CurrentUser get() { return CURRENT.get(); }
    public static CurrentUser require() {
        CurrentUser user = get();
        if (user == null) throw new BusinessException(401, "请先登录");
        return user;
    }
    public static void clear() { CURRENT.remove(); }
}
