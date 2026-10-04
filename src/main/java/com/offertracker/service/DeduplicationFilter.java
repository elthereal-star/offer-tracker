package com.offertracker.service;

/** Probabilistic duplicate hint; callers must still enforce uniqueness in MySQL. */
public interface DeduplicationFilter { boolean mightContain(String key); void put(String key); }
