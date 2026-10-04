package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.BitSet;

@Service
@Profile("!redis")
public class LocalDeduplicationFilter implements DeduplicationFilter {
    private final BitSet bits = new BitSet(1 << 20); private final Object monitor = new Object();
    @Override public boolean mightContain(String key) { int first = hash(key, 0), second = hash(key, 1); synchronized (monitor) { return bits.get(first) && bits.get(second); } }
    @Override public void put(String key) { int first = hash(key, 0), second = hash(key, 1); synchronized (monitor) { bits.set(first); bits.set(second); } }
    private int hash(String key, int seed) { return Math.floorMod((key == null ? 0 : key.hashCode()) * 31 + seed * 0x9e3779b9, 1 << 20); }
}
