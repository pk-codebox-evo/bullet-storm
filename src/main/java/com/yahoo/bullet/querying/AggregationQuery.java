/*
 *  Copyright 2016, Yahoo Inc.
 *  Licensed under the terms of the Apache License, Version 2.0.
 *  See the LICENSE file associated with the project for terms.
 */
package com.yahoo.bullet.querying;

import com.yahoo.bullet.parsing.ParsingException;
import com.yahoo.bullet.result.Clip;
import lombok.Getter;

import java.util.Map;

public class AggregationQuery extends AbstractQuery<byte[], Clip> {
    @Getter
    protected long lastAggregationTime = 0L;

    /**
     * Constructor that takes a String representation of the query.
     *
     * @param queryString The query as a string.
     * @param configuration A map of configurations to use.
     * @throws ParsingException if there was an issue.
     */
    public AggregationQuery(String queryString, Map configuration) throws ParsingException {
        super(queryString, configuration);
    }

    @Override
    public boolean consume(byte[] data) {
        specification.aggregate(data);
        // If the specification is no longer accepting data, then the Query has been satisfied.
        return !specification.isAcceptingData();
    }

    /**
     * {@inheritDoc}
     *
     * Get the aggregate so far.
     *
     * @return A non-null aggregated resulting {@link Clip}.
     */
    @Override
    public Clip getData() {
        Clip result = specification.getAggregate();
        lastAggregationTime = System.currentTimeMillis();
        return result;
    }
}
