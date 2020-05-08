package org.apereo.cas.couchbase.core;

import org.apereo.cas.configuration.model.support.couchbase.BaseCouchbaseProperties;
import org.apereo.cas.configuration.support.Beans;
import org.apereo.cas.util.CollectionUtils;

import com.couchbase.client.core.env.IoConfig;
import com.couchbase.client.core.env.NetworkResolution;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.env.TimeoutConfig;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryStatus;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A factory class which produces a client for a particular Couchbase getBucket.
 * A design consideration was that we want the server to start even if Couchbase
 * is unavailable, picking up the connection when Couchbase comes online. Hence
 * the creation of the client is made using a scheduled task which is repeated
 * until successful connection is made.
 *
 * @author Fredrik Jönsson "fjo@kth.se"
 * @author Misagh Moayyed
 * @since 4.2
 */
@Slf4j
@Getter
public class CouchbaseClientFactory {
    static {
        System.setProperty("com.couchbase.queryEnabled", "true");
    }

    private final BaseCouchbaseProperties properties;

    private Cluster cluster;

    public CouchbaseClientFactory(final BaseCouchbaseProperties properties) {
        this.properties = properties;

        initializeCluster();
    }

    /**
     * Inverse of connectBucket, shuts down the client, cancelling connection
     * task if not completed.
     */
    @SneakyThrows
    public void shutdown() {
        if (this.cluster != null) {
            LOGGER.debug("Disconnecting from Couchbase cluster");
            this.cluster.disconnect();
        }
    }

    private void initializeCluster() {
        shutdown();
        val nodes = StringUtils.commaDelimitedListToSet(properties.getNodeSet());
        LOGGER.debug("Initializing Couchbase cluster for nodes [{}]", nodes);

        val env = ClusterEnvironment
            .builder()
            .timeoutConfig(TimeoutConfig
                .connectTimeout(getConnectionTimeout())
                .kvTimeout(getKvTimeout())
                .queryTimeout(getQueryTimeout())
                .searchTimeout(getSearchTimeout())
                .viewTimeout(getViewTimeout()))
            .ioConfig(IoConfig
                .maxHttpConnections(properties.getMaxHttpConnections())
                .networkResolution(NetworkResolution.AUTO))
            .build();

        val listOfNodes = nodes.stream()
            .map(SeedNode::create)
            .collect(Collectors.toSet());

        val options = ClusterOptions
            .clusterOptions(properties.getClusterUsername(), properties.getClusterPassword())
            .environment(env);
        this.cluster = Cluster.connect(listOfNodes, options);
    }

    public Duration getConnectionTimeout() {
        return Beans.newDuration(properties.getConnectionTimeout());
    }

    public Duration getSearchTimeout() {
        return Beans.newDuration(properties.getSearchTimeout());
    }

    public Duration getQueryTimeout() {
        return Beans.newDuration(properties.getQueryTimeout());
    }

    public Duration getViewTimeout() {
        return Beans.newDuration(properties.getViewTimeout());
    }

    public Duration getKvTimeout() {
        return Beans.newDuration(properties.getKvTimeout());
    }

    /**
     * Query with parameters.
     *
     * @param query      the query
     * @param parameters the parameters
     * @return the query result
     */
    public QueryResult query(final String query, final Optional<JsonObject> parameters) {
        val formattedQuery = String.format("SELECT * FROM `%s` WHERE %s", properties.getBucket(), query);
        val result = parameters.isPresent()
            ? cluster.query(formattedQuery, QueryOptions.queryOptions().parameters(parameters.get()))
            : cluster.query(formattedQuery);
        if (result.metaData().status() == QueryStatus.ERRORS) {
            throw new CouchbaseException("Could not execute query");
        }
        return result;
    }

    /**
     * Query and get a result by username.
     *
     * @param statement the query
     * @return the n1ql query result
     */
    public QueryResult query(final String statement) {
        return query(statement, Optional.empty());
    }

    /**
     * Collect attributes from entity map.
     *
     * @param couchbaseEntity the couchbase entity
     * @param filter          the filter
     * @return the map
     */
    public Map<String, List<Object>> collectAttributesFromEntity(final JsonObject couchbaseEntity,
                                                                 final Predicate<String> filter) {
        return couchbaseEntity.getNames()
            .stream()
            .filter(filter)
            .map(name -> Pair.of(name, couchbaseEntity.get(name)))
            .collect(Collectors.toMap(Pair::getKey, s -> CollectionUtils.wrapList(s.getValue())));
    }

    /**
     * Bucket upsert default collection.
     *
     * @param content the content
     * @return the mutation result
     */
    public MutationResult bucketUpsertDefaultCollection(final String content) {
        val id = UUID.randomUUID().toString();
        val document = JsonObject.fromJson(content);
        return bucketUpsertDefaultCollection(id, document);
    }

    /**
     * Bucket upsert default collection.
     *
     * @param id       the id
     * @param document the document
     * @return the mutation result
     */
    public MutationResult bucketUpsertDefaultCollection(final String id, final Object document) {
        val bucket = this.cluster.bucket(properties.getBucket());
        return bucket.defaultCollection().upsert(id, document);
    }

    /**
     * Bucket remove from default collection.
     *
     * @param id the id
     * @return the mutation result
     */
    public MutationResult bucketRemoveFromDefaultCollection(final String id) {
        val bucket = this.cluster.bucket(properties.getBucket());
        return bucket.defaultCollection().remove(id);
    }

    /**
     * Gets bucket.
     *
     * @return the bucket
     */
    public String getBucket() {
        return properties.getBucket();
    }

    /**
     * Bucket get get result.
     *
     * @param id the id
     * @return the get result
     */
    public GetResult bucketGet(final String id) {
        val bucket = this.cluster.bucket(properties.getBucket());
        return bucket.defaultCollection().get(id);
    }
}

