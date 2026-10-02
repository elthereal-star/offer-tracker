package com.offertracker.service;

public interface IpRequestLimiter {
    void checkAllowed(String ip);
}
