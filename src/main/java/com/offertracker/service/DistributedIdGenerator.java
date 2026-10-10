package com.offertracker.service;

/** Monotonic, process-safe identifier boundary for future high-volume entities. */
public interface DistributedIdGenerator { long nextId(); }
