package com.distributed.idgen.common;

/**
 * Metadata describing the structural components of a generated ID.
 *
 * <p>
 * Provides a human-readable breakdown useful for debugging and observability.
 * </p>
 *
 * @param rawId       The raw ID value as a string
 * @param strategy    The generator strategy name
 * @param bitLength   Total bit length of the ID (e.g. 64 for Snowflake, 128 for
 *                    UUID)
 * @param description A detailed description of the ID layout and its components
 */
public record IdMetadata(String rawId,String strategy,int bitLength,String description){

@Override public String toString(){return String.format("[%s] ID=%s (%d-bit) | %s",strategy,rawId,bitLength,description);}}
