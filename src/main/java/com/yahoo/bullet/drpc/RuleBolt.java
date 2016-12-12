/*
 *  Copyright 2016, Yahoo Inc.
 *  Licensed under the terms of the Apache License, Version 2.0.
 *  See the LICENSE file associated with the project for terms.
 */
package com.yahoo.bullet.drpc;

import com.yahoo.bullet.tracing.AbstractRule;
import lombok.extern.slf4j.Slf4j;
import backtype.storm.Config;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.tuple.Tuple;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class RuleBolt<R extends AbstractRule> implements IRichBolt {
    public static final Integer DEFAULT_TICK_INTERVAL = 5;
    protected int tickInterval;
    protected Map configuration;
    protected OutputCollector collector;
    // TODO consider a rotating map with multilevels and reinserts upon rotating instead for scalability
    protected Map<Long, R> rulesMap;

    /**
     * Constructor that accepts the tick interval.
     * @param tickInterval The tick interval in seconds.
     */
    public RuleBolt(Integer tickInterval) {
        this.tickInterval = tickInterval == null ? DEFAULT_TICK_INTERVAL : tickInterval;
    }

    /**
     * Default constructor.
     */
    public RuleBolt() {
        this(DEFAULT_TICK_INTERVAL);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.configuration = stormConf;
        this.collector = collector;
        rulesMap = new LinkedHashMap<>();
    }

    /**
     * Retires DRPC rules that are active past the tick time.
     * @return The map of DRPC request ids to Rules that were retired.
     */
    protected Map<Long, R> retireRules() {
        Map<Long, R> retiredRules = rulesMap.entrySet().stream().filter(e -> e.getValue().isExpired())
                                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        rulesMap.keySet().removeAll(retiredRules.keySet());
        if (retiredRules.size() > 0) {
            log.info("Retired {} rule(s). There are {} active rule(s).", retiredRules.size(), rulesMap.size());
        }
        return retiredRules;
    }

    /**
     * Initializes a rule from a rule tuple.
     * @param tuple The rule tuple with the rule to initialize.
     */
    protected void initializeRule(Tuple tuple) {
        Long id = tuple.getLong(TopologyConstants.ID_POSITION);
        String ruleString = tuple.getString(TopologyConstants.RULE_POSITION);
        R rule = getRule(id, ruleString);
        if (rule == null) {
            log.error("Failed to initialize rule for request {} with rule {}", id, ruleString);
            return;
        }
        log.info("Initialized rule {} : {}", id, rule.toString());
        rulesMap.put(id, rule);
    }

    /**
     * Gets the default tick configuration to be used.
     * @return A Map configuration containing the default tick configuration.
     */
    public Map<String, Object> getDefaultTickConfiguration() {
        Config conf = new Config();
        conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, tickInterval);
        return conf;
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return getDefaultTickConfiguration();
    }

    @Override
    public void cleanup() {
    }

    /**
     * Finds the right type of AbstractRule to use for this Bolt. If rule cannot be
     * created, handles the error and returns null.
     *
     * @param id The DRPC request id.
     * @param ruleString The String version of the AbstractRule
     * @return The appropriate type of AbstractRule to use for this Bolt.
     */
    protected abstract R getRule(Long id, String ruleString);
}
