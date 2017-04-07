package com.psddev.dari.elasticsearch;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.psddev.dari.db.AbstractDatabase;
import com.psddev.dari.db.AbstractGrouping;
import com.psddev.dari.db.AtomicOperation;
import com.psddev.dari.db.ComparisonPredicate;
import com.psddev.dari.db.CompoundPredicate;
import com.psddev.dari.db.Database;
import com.psddev.dari.db.Grouping;
import com.psddev.dari.db.Location;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectIndex;
import com.psddev.dari.db.ObjectMethod;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Predicate;
import com.psddev.dari.db.PredicateParser;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Record;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.db.Region;
import com.psddev.dari.db.Sorter;
import com.psddev.dari.db.State;
import com.psddev.dari.db.StateSerializer;
import com.psddev.dari.db.UnsupportedIndexException;
import com.psddev.dari.db.UnsupportedPredicateException;
import com.psddev.dari.db.UpdateNotifier;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.PaginatedResult;
import com.psddev.dari.util.SparseSet;
import com.psddev.dari.util.UuidUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.bulk.Retry;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.ShapeRelation;
import com.vividsolutions.jts.geom.Coordinate;
import org.elasticsearch.common.geo.builders.ShapeBuilders;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.GeoShapeQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders.weightFactorFunction;
import static org.elasticsearch.search.sort.SortOrder.ASC;
import static org.elasticsearch.search.sort.SortOrder.DESC;

/**
 * ElasticsearchDatabase for Elasticsearch 5.2
 *
 */
public class ElasticsearchDatabase extends AbstractDatabase<TransportClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchDatabase.class);

    public static final String ELASTIC_VERSION = "5.2.2";
    public static final String DEFAULT_DATABASE_NAME = "dari/defaultDatabase";
    public static final String DATABASE_NAME = "elasticsearch";
    public static final String SETTING_KEY_PREFIX = "dari/database/" + DATABASE_NAME + "/";
    public static final String CLUSTER_NAME_SUB_SETTING = "clusterName";
    public static final String CLUSTER_PORT_SUB_SETTING = "clusterPort";
    public static final String CLUSTER_REST_PORT_SUB_SETTING = "clusterRestPort";
    public static final String HOSTNAME_SUB_SETTING = "clusterHostname";
    public static final String INDEX_NAME_SUB_SETTING = "indexName";
    public static final String SHARDS_MAX_SETTING = "shardsMax";
    public static final String SEARCH_TIMEOUT_SETTING = "searchTimeout";
    public static final String SUBQUERY_RESOLVE_LIMIT_SETTING = "subQueryResolveLimit";
    public static final String DEFAULT_DATAFIELD_TYPE_SETTING = "defaultDataFieldType";
    public static final String DATA_TYPE_RAW_SETTING = "dataTypesRaw";
    public static final String JSON_DATAFIELD_TYPE = "json";
    public static final String RAW_DATAFIELD_TYPE = "raw";

    public static final String DATA_FIELD = "data";
    public static final String ID_FIELD = "_id";
    public static final String IDS_FIELD = "_ids";
    public static final String UID_FIELD = "_uid";      // special for aggregations/sort
    public static final String TYPE_ID_FIELD = "_type";
    public static final String DEFAULT_SUGGEST_FIELD = "suggestField";
    // note that _any has special features, it indexes even non indexed fields so it must be stored for reindexing
    // elastic has _all but not enough control for ExcludeFromAny, etc. So not using that.
    public static final String ANY_FIELD = "_any";
    public static final String UPDATEDATE_FIELD = "updateDate";
    public static final String JSONINDEX_SUB_NAME = "json";
    public static final int INITIAL_FETCH_SIZE = 1000;
    public static final int SUBQUERY_MAX_ROWS = 5000;   // dari/subQueryResolveLimit
    public static final int TIMEOUT = 30000;            // 30 seconds
    public static final int MAX_BINARY_FIELD_LENGTH = 1024;
    public static final int FACET_MAX_ROWS = 100;
    public static final int CACHE_TIMEOUT_MIN = 60;
    public static final int CACHE_MAX_INDEX_SIZE = 5000;
    private static final long MILLISECONDS_IN_5YEAR = 1000L * 60L * 60L * 24L * 365L * 5L;
    private static final Pattern UUID_PATTERN = Pattern.compile("([A-Fa-f0-9]{8})-([A-Fa-f0-9]{4})-([A-Fa-f0-9]{4})-([A-Fa-f0-9]{4})-([A-Fa-f0-9]{12})");
    public static final String SCORE_EXTRA = "elastic.score";
    public static final String NORMALIZED_SCORE_EXTRA = "elastic.normalizedScore";
    private static final String ELASTIC_MAPPING = getMapping("");
    private static final String ELASTIC_SETTING = getSetting("");

    public class IndexKey {
        private String indexId;
        private int shardsMax;
        private List<ElasticsearchNode> clusterNodes;
        private org.elasticsearch.common.settings.Settings nodeSettings;

        public int getShardsMax() {
            return shardsMax;
        }

        public void setShardsMax(int shardsMax) {
            this.shardsMax = shardsMax;
        }

        public String getIndexId() {
            return indexId;
        }

        public void setIndexId(String indexId) {
            this.indexId = indexId;
        }

        public List<ElasticsearchNode> getClusterNodes() {
            return clusterNodes;
        }

        public void setClusterNodes(List<ElasticsearchNode> clusterNodes) {
            this.clusterNodes = clusterNodes;
        }

        public org.elasticsearch.common.settings.Settings getNodeSettings() {
            return nodeSettings;
        }

        public void setNodeSettings(org.elasticsearch.common.settings.Settings nodeSettings) {
            this.nodeSettings = nodeSettings;
        }

        @Override
        public String toString() {

            return MoreObjects.toStringHelper(this)
                    .add("indexId", indexId)
                    .toString();

        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IndexKey indexKey = (IndexKey) o;

            return indexId.equals(indexKey.indexId);
        }

        @Override
        public int hashCode() {
            return indexId.hashCode();
        }
    }

    /**
     * CREATE_INDEX_CACHE indicates if the index has been setup in Elastic
     */
    private static final LoadingCache<IndexKey, String> CREATE_INDEX_CACHE =
            CacheBuilder.newBuilder()
                    .maximumSize(CACHE_MAX_INDEX_SIZE)
                    .expireAfterAccess(CACHE_TIMEOUT_MIN, TimeUnit.MINUTES)
                    .build(new CacheLoader<IndexKey, String>() {

                        @Override
                        public String load(IndexKey index) throws Exception {
                            TransportClient client = ElasticsearchDatabaseConnection.getClient(index.getNodeSettings(), index.getClusterNodes());
                            defaultMap(client, index.getIndexId(), index.getShardsMax());
                            LOGGER.debug("Elasticsearch creating index [{}]", index.getIndexId());
                            return "setIndex";
                        }
                    });

    /**
     * INDEXTYPEID_CACHE indicates if the TypeId requires the index to be unique due to rules around RAW_DATAFIELD_TYPE
     */
    private static final LoadingCache<UUID, String> INDEXTYPEID_CACHE =
            CacheBuilder.newBuilder()
                    .maximumSize(CACHE_MAX_INDEX_SIZE)
                    .expireAfterAccess(CACHE_TIMEOUT_MIN, TimeUnit.MINUTES)
                    .build(new CacheLoader<UUID, String>() {

                        @Override
                        public String load(UUID index) throws Exception {
                            String indexName = JSONINDEX_SUB_NAME;
                            if (index != null) {
                                ElasticsearchDatabase db = Database.Static.getFirst(ElasticsearchDatabase.class);
                                if (db != null) {
                                    ObjectType type = db.getEnvironment().getTypeById(index);
                                    if (type != null) {
                                        if (db.isJsonGroup(type.getGroups())) {
                                            indexName = JSONINDEX_SUB_NAME;
                                        } else {
                                            indexName = index.toString().replaceAll("-", "");
                                        }
                                    } else if (db.defaultDataFieldType.equals(RAW_DATAFIELD_TYPE)) {
                                        indexName = index.toString().replaceAll("-", "");
                                    }
                                }
                            }
                            return indexName;
                        }
                    });

    private final List<UpdateNotifier<?>> updateNotifiers = new ArrayList<>();

    public static final String LOCATION_FIELD = "_location";
    public static final String BOOLEAN_FIELD = "_boolean";
    public static final String STRING_FIELD = "_string";
    public static final String DATE_FIELD = "_date";
    public static final String NUMBER_FIELD = "_number";
    public static final String REGION_FIELD = "_polygon";
    public static final String RAW_FIELD = STRING_FIELD + ".raw";   // UUID, String not text, and RECORD
    public static final String MATCH_FIELD = STRING_FIELD + ".match";
    public static final String SUGGEST_FIELD = "_suggest";

    private final List<ElasticsearchNode> clusterNodes = new ArrayList<>();
    private org.elasticsearch.common.settings.Settings nodeSettings;

    private String clusterName;
    private String indexName;
    private int searchTimeout = TIMEOUT;
    private String defaultDataFieldType = JSON_DATAFIELD_TYPE;
    private String dataTypesRaw = null;
    private SparseSet dataTypesRawSparseSet = null;
    private int subQueryResolveLimit = SUBQUERY_MAX_ROWS;
    private boolean hasGroup = false;
    private transient TransportClient client;
    private boolean painlessModule = false;
    private int shardsMax = 1000;   // default provided by Elastic

    /**
     * get the Nodes for the Cluster
     */
    public List<ElasticsearchNode> getClusterNodes() {
        return clusterNodes;
    }

    /**
     * The amount of rows per subquery(join) Elastic Search wrapped
     *
     * @see #readAll(Query)
     */
    public void setSubQueryResolveLimit(int subQueryResolveLimit) {

        this.subQueryResolveLimit = subQueryResolveLimit;
    }

    /**
     * The amount of rows per subquery(join) to Elastic Search
     *
     * @see #readAll(Query)
     */
    public int getSubQueryResolveLimit() {

        return subQueryResolveLimit;
    }

    /**
     * Set the timeout for calls into Elastic - might want to set lower/higher based on systems
     * Used in .setTimeout(new TimeValue(this.searchTimeout))
     *
     * @see #doInitialize
     */
    public void setSearchTimeout(int searchTimeout) {

        this.searchTimeout = searchTimeout;
    }

    /**
     * The timeout for calls to Elastic
     *
     * @see #readPartial(Query, long, int)
     */
    public int getSearchTimeout() {

        return searchTimeout;
    }

    /**
     * Set the ClusterName for Elastic
     *
     * @see #doInitialize
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * Get the ClusterName for Elastic
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Get the indexName for Elastic
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Set the index for Elastic
     *
     * @see #doInitialize
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * The Elastic TransportClient using ElasticsearchDatabaseConnection.getClient()
     *
     * @return {@code null} indicates could not get client connection
     */
    @Override
    public TransportClient openConnection() {

        if (this.client != null && isAlive(this.client)) {
            return this.client;
        }
        try {
            this.client = ElasticsearchDatabaseConnection.getClient(nodeSettings, clusterNodes);
            return this.client;
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Elasticsearch openConnection Cannot open ES Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        LOGGER.info("Elasticsearch openConnection return null");
        return null;
    }

    /**
     * Close the connection. Actually does not close() since the connection is persistent
     */
    @Override
    public void closeConnection(TransportClient client) {
        //client.close();
    }

    /**
     * Initialize all the settings for Elastic
     */
    @Override
    protected void doInitialize(String settingsKey, Map<String, Object> settings) {

        String groupsPattern = ObjectUtils.to(String.class, settings.get(GROUPS_SUB_SETTING));

        if (groupsPattern == null || ObjectUtils.isBlank(groupsPattern) || groupsPattern.equals("+/")) {
            this.hasGroup = false;
        } else {
            this.hasGroup = true;
        }

        String clusterName = ObjectUtils.to(String.class, settings.get(CLUSTER_NAME_SUB_SETTING));

        Preconditions.checkNotNull(clusterName);

        this.clusterName = clusterName;

        String indexName = ObjectUtils.to(String.class, settings.get(INDEX_NAME_SUB_SETTING));

        Preconditions.checkNotNull(indexName);

        String subQueryResolveLimit = ObjectUtils.to(String.class, settings.get(SUBQUERY_RESOLVE_LIMIT_SETTING));

        if (subQueryResolveLimit == null) {
            this.subQueryResolveLimit = SUBQUERY_MAX_ROWS;
        } else {
            this.subQueryResolveLimit = Integer.parseInt(subQueryResolveLimit);
        }

        String clusterTimeout = ObjectUtils.to(String.class, settings.get(SEARCH_TIMEOUT_SETTING));

        if (clusterTimeout == null) {
            this.searchTimeout = TIMEOUT;
        } else {
            this.searchTimeout = Integer.parseInt(clusterTimeout);

        }

        String defaultDataFieldType = ObjectUtils.to(String.class, settings.get(DEFAULT_DATAFIELD_TYPE_SETTING));

        if (defaultDataFieldType == null) {
            this.defaultDataFieldType = JSON_DATAFIELD_TYPE;
        } else {
            if (!defaultDataFieldType.equals(JSON_DATAFIELD_TYPE) && !defaultDataFieldType.equals(RAW_DATAFIELD_TYPE)) {
                this.defaultDataFieldType = JSON_DATAFIELD_TYPE;
            } else {
                this.defaultDataFieldType = defaultDataFieldType;
            }
        }

        this.dataTypesRaw = ObjectUtils.to(String.class, settings.get(DATA_TYPE_RAW_SETTING));
        if (this.dataTypesRaw != null) {
            this.dataTypesRawSparseSet = new SparseSet(this.dataTypesRaw);
        }

        if (this.defaultDataFieldType.equals(RAW_DATAFIELD_TYPE) && this.dataTypesRaw != null) {
            this.dataTypesRaw = null;
            LOGGER.warn("Setting Conflict - when " + DEFAULT_DATAFIELD_TYPE_SETTING + " is " + RAW_DATAFIELD_TYPE + " cannot set " + DATA_TYPE_RAW_SETTING);
            LOGGER.warn("Turning off " + DATA_TYPE_RAW_SETTING);
        }

        String shardsMax = ObjectUtils.to(String.class, settings.get(SHARDS_MAX_SETTING));
        if (shardsMax != null) {
            this.shardsMax = Integer.parseInt(shardsMax);
        }

        boolean done = false;
        int nodeCount = 1;
        while (!done) {

            if (settings.get(String.valueOf(nodeCount)) == null) {
                done = true;
            } else {

                @SuppressWarnings("unchecked")
                Map<String, Object> subSettings = (Map<String, Object>) settings.get(String.valueOf(nodeCount));

                String clusterPort = ObjectUtils.to(String.class, subSettings.get(CLUSTER_PORT_SUB_SETTING));

                Preconditions.checkNotNull(clusterPort);

                String clusterRestPort = ObjectUtils.to(String.class, subSettings.get(CLUSTER_REST_PORT_SUB_SETTING));

                Preconditions.checkNotNull(clusterRestPort);

                String clusterHostname = ObjectUtils.to(String.class, subSettings.get(HOSTNAME_SUB_SETTING));

                Preconditions.checkNotNull(clusterHostname);

                ElasticsearchNode n = new ElasticsearchNode();
                n.setHostname(clusterHostname);
                n.setPort(Integer.parseInt(clusterPort));
                n.setRestPort(Integer.parseInt(clusterRestPort));

                this.clusterNodes.add(n);

                nodeCount++;
            }
        }

        this.indexName = indexName;

        this.nodeSettings = org.elasticsearch.common.settings.Settings.builder()
                .put("cluster.name", this.clusterName)
                .put("client.transport.sniff", true).build();

        this.painlessModule = this.isModuleInstalled("lang-painless", "org.elasticsearch.painless.PainlessPlugin");

    }

    /**
     * Override to support in Elastic
     */
    @Override
    public Date readLastUpdate(Query<?> query) {
        return readUpdateMax(query);
    }

    public boolean isAlive(TransportClient client) {
        if (client != null) {
            List<DiscoveryNode> nodes = client.connectedNodes();
            if (!nodes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get The running version of Elastic
     *
     * @return the version running can return null on exception
     */
    public static String getVersion(String nodeHost) {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            HttpGet getRequest = new HttpGet(nodeHost);
            getRequest.addHeader("accept", "application/json");
            CloseableHttpResponse response = httpClient.execute(getRequest);
            try {
                HttpEntity entity = response.getEntity();
                String json = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
                JSONObject j = new JSONObject(json);
                if (j != null) {
                    if (j.get("version") != null) {
                        if (j.getJSONObject("version") != null) {
                            JSONObject jo = j.getJSONObject("version");
                            String version = jo.getString("number");
                            if (!ELASTIC_VERSION.equals(version)) {
                                LOGGER.warn("Warning: Elasticsearch {} version is not {}", version, ELASTIC_VERSION);
                            }
                            return version;
                        }
                    }
                }
            } finally {
                response.close();
            }
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Warning: Elasticsearch cannot get version [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        return null;
    }

    /**
     * Get the clusterName from the running version
     *
     * @return the clusterName
     */
    public static String getClusterName(String nodeHost) {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet getRequest = new HttpGet(nodeHost);
            getRequest.addHeader("accept", "application/json");
            CloseableHttpResponse response = httpClient.execute(getRequest);
            try {
                HttpEntity entity = response.getEntity();
                String json = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
                JSONObject j = new JSONObject(json);
                if (j != null) {
                    if (j.get("cluster_name") != null) {
                        return j.getString("cluster_name");
                    }
                }
            } finally {
                response.close();
            }
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Warning: Elasticsearch cannot get cluster_name [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        return null;
    }

    /**
     * Check to see if the running version is alive.
     *
     * @return true indicates node is alive
     */
    public static boolean checkAlive(String nodeHost) {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet getRequest = new HttpGet(nodeHost);
            getRequest.addHeader("accept", "application/json");
            CloseableHttpResponse response = httpClient.execute(getRequest);
            try {
                HttpEntity entity = response.getEntity();
                String json = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
                JSONObject j = new JSONObject(json);
                if (j != null) {
                    if (j.get("cluster_name") != null) {
                        return true;
                    }
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Warning: Elasticsearch is not already running");
        }
        return false;
    }

    /**
     * Get one node and return it for REST call
     */
    public static String getNodeHost(String host, String restPort) {
        return "http://" + host + ":" + restPort + "/";
    }

    /**
     * Get one node and return it for REST call
     */
    public String getNodeHost() {
        return "http://" + clusterNodes.get(0).getHostname() + ":" + clusterNodes.get(0).getRestPort() + "/";
    }

    /**
     * See if painless Elastic module is installed. This API does not work well here.
     */
    public boolean isModuleInstalled(String moduleName, String pluginName) {

        try {
            String nodes = getNodeHost() + "_nodes";
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet get = new HttpGet(nodes);
            get.addHeader("accept", "application/json");
            CloseableHttpResponse response = httpClient.execute(get);
            try {
                HttpEntity entity = response.getEntity();
                String json = EntityUtils.toString(entity);
                EntityUtils.consume(entity);

                JSONObject j = new JSONObject(json);
                if (j != null) {
                    if (j.get("nodes") != null) {
                        if (j.getJSONObject("nodes") != null) {
                            JSONObject jo = j.getJSONObject("nodes");
                            Iterator<?> keys = jo.keys();

                            while (keys.hasNext()) {
                                String key = (String) keys.next();
                                if (jo.get(key) instanceof JSONObject) {
                                    JSONObject node = (JSONObject) jo.get(key);
                                    JSONArray modules = node.getJSONArray("modules");
                                    for (int i = 0; i < modules.length(); i++) {
                                        JSONObject module = modules.getJSONObject(i);
                                        String name = (String) module.get("name");
                                        if (name.equals(moduleName)) {
                                            return true;
                                        }
                                    }
                                    JSONArray plugins = node.getJSONArray("plugins");
                                    for (int i = 0; i < plugins.length(); i++) {
                                        JSONObject plugin = plugins.getJSONObject(i);
                                        String name = (String) plugin.get("name");
                                        if (name.equals(pluginName)) {
                                            return true;
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            } finally {
                response.close();
            }
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Warning: Elasticsearch cannot check Painless [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);

        }

        return false;
    }

    /**
     * Grab a connection and check it
     */
    public boolean isAlive() {
        TransportClient client = openConnection();
        if (client != null) {
            List<DiscoveryNode> nodes = client.connectedNodes();
            closeConnection(client);
            if (!nodes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return results for Grouping in Elastic
     */
    @Override
    public <T> PaginatedResult<Grouping<T>> readPartialGrouped(Query<T> query, long offset, int limit, String... fields) {
        if (fields == null || fields.length != 1) {
            return super.readPartialGrouped(query, offset, limit, fields);
        }

        List<Grouping<T>> groupings = new ArrayList<>();

        TransportClient client = openConnection();
        if (client == null || !isAlive(client)) {
            return null;
        }

        Set<UUID> typeIds = query.getConcreteTypeIds(this);

        if (query.getGroup() != null && typeIds.size() == 0) {
            // should limit by the type
            LOGGER.debug("Elasticsearch PaginatedResult readPartialGrouped the call is to limit by from() but did not load typeIds! [{}]", query.getGroup());
        }
        String[] typeIdStrings = typeIds.size() == 0
                ? new String[]{}
                : typeIds.stream().map(UUID::toString).toArray(String[]::new);

        List<String> indexNames = new ArrayList<>();
        for (UUID u : typeIds) {
            indexNames.add(getIndexName() + getElasticIndexName(u));
        }
        String[] indexIdStrings = indexNames.toArray(new String[0]);
        checkIndexes(indexIdStrings);

        SearchResponse response;
        QueryBuilder qb = predicateToQueryBuilder(query.getPredicate(), query);
        SearchRequestBuilder srb;

        Matcher groupingMatcher = Query.RANGE_PATTERN.matcher(fields[0]);
        if (groupingMatcher.find()) {
            String field = groupingMatcher.group(1);
            Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, field);
            if (mappedKey != null) {
                String internalType = mappedKey.getInternalType();
                if (internalType != null) {
                    field = addQueryFieldType(internalType, mappedKey.getIndexKey(null), true);
                }
            }
            if (field == null) {
                field = mappedKey.getIndexKey(null);
            }
            Double start = ObjectUtils.to(Double.class, groupingMatcher.group(2).trim());
            Double end = ObjectUtils.to(Double.class, groupingMatcher.group(3).trim());
            Double gap = ObjectUtils.to(Double.class, groupingMatcher.group(4).trim());

            if (typeIds.size() > 0) {
                srb = client.prepareSearch(indexIdStrings)
                        .setFetchSource(!query.isReferenceOnly())
                        .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
                srb.setTypes(typeIdStrings);
            } else {
                srb = client.prepareSearch(getIndexName() + "*")
                        .setFetchSource(!query.isReferenceOnly())
                        .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
            }
            srb.setQuery(qb)
                    .setFrom(0)
                    .setSize(0);

            RangeAggregationBuilder ab = AggregationBuilders.range("agg").field(field);
            for (double i = start; i < end; i = i + gap) {
                ab.addRange(i, i + gap);
            }
            srb.addAggregation(ab);
            LOGGER.debug("Elasticsearch readPartialGrouped typeIds [{}] - [{}]", (typeIdStrings.length == 0 ? "" : typeIdStrings), srb.toString());
            response = srb.execute().actionGet();
            SearchHits hits = response.getHits();

            Aggregations aggregations = response.getAggregations();

            if (aggregations != null) {
                Range agg = aggregations.get("agg");

                for (Range.Bucket entry : agg.getBuckets()) {
                    String key = entry.getKeyAsString();             // Range as key
                    Number from = (Number) entry.getFrom();          // Bucket from
                    Number to = (Number) entry.getTo();              // Bucket to
                    long docCount = entry.getDocCount();             // Doc count

                    LOGGER.debug("hits [{}], key [{}], from [{}], to [{}], doc_count [{}]",  new Object[] {hits.getTotalHits(), key, from, to, docCount});
                    groupings.add(new ElasticGrouping<>(Collections.singletonList(key), query, fields, docCount));
                }
            }

        } else {
            if (typeIds.size() > 0) {
                srb = client.prepareSearch(indexIdStrings)
                        .setFetchSource(!query.isReferenceOnly())
                        .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
                srb.setTypes(typeIdStrings);
            } else {
                srb = client.prepareSearch(getIndexName() + "*")
                        .setFetchSource(!query.isReferenceOnly())
                        .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
            }

            srb.setQuery(qb)
                    .setFrom(0)
                    .setSize(0);

            Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, fields[0]);
            String elasticField = specialSortFields.get(mappedKey);
            if (elasticField == null) {
                if (mappedKey != null) {
                    String internalType = mappedKey.getInternalType();
                    if (internalType != null) {
                        elasticField = addQueryFieldType(internalType, mappedKey.getIndexKey(null), true);
                    }
                }
                if (elasticField == null) {
                    elasticField = mappedKey.getIndexKey(null);
                }
            }

            if (limit > FACET_MAX_ROWS) {
                limit = FACET_MAX_ROWS;
            }

            if (query.getGroup() != null) {
                TermsAggregationBuilder ab = AggregationBuilders.terms("agg").field(elasticField)
                        .size(limit);
                for (Terms.Order termsOrder : predicateToSortBuilderGrouping(query.getSorters(), query, elasticField)) {
                    ab.order(termsOrder);
                }
                srb.addAggregation(ab);
            }
            LOGGER.debug("Elasticsearch readPartialGrouped typeIds [{}] - [{}]", (typeIdStrings.length == 0 ? "" : typeIdStrings), srb.toString());
            response = srb.execute().actionGet();
            SearchHits hits = response.getHits();

            Aggregations aggregations = response.getAggregations();

            if (aggregations != null) {
                Terms agg = aggregations.get("agg");

                for (Terms.Bucket entry : agg.getBuckets()) {
                    String key = entry.getKeyAsString();    // Term
                    long docCount = entry.getDocCount();    // Doc count
                    LOGGER.debug("key [{}], doc_count [{}]", key, docCount);
                    groupings.add(new ElasticGrouping<>(Collections.singletonList(key), query, fields, docCount));
                }
            }
        }

        return new PaginatedResult<>(offset, limit, groupings);
    }

    /**
     * Return results for Max() updateDate
     */
    public <T> Date readUpdateMax(Query<T> query) {

        TransportClient client = openConnection();
        if (client == null || !isAlive(client)) {
            return null;
        }

        Set<UUID> typeIds = query.getConcreteTypeIds(this);

        if (query.getGroup() != null && typeIds.size() == 0) {
            // should limit by the type
            LOGGER.debug("Elasticsearch readUpdateMax is to limit by from() but did not load typeIds! [{}]", query.getGroup());
        }
        String[] typeIdStrings = typeIds.size() == 0
                ? new String[]{}
                : typeIds.stream().map(UUID::toString).toArray(String[]::new);

        List<String> indexNames = new ArrayList<>();
        for (UUID u : typeIds) {
            indexNames.add(getIndexName() + getElasticIndexName(u));
        }
        String[] indexIdStrings = indexNames.toArray(new String[0]);
        checkIndexes(indexIdStrings);

        SearchResponse response;
        QueryBuilder qb = predicateToQueryBuilder(query.getPredicate(), query);
        SearchRequestBuilder srb;

        if (typeIds.size() > 0) {
            srb = client.prepareSearch(indexIdStrings)
                    .setFetchSource(!query.isReferenceOnly())
                    .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
            srb.setTypes(typeIdStrings);
        } else {
            srb = client.prepareSearch(getIndexName() + "*")
                    .setFetchSource(!query.isReferenceOnly())
                    .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
        }

        srb.setQuery(qb)
                .setFrom(0)
                .setSize(0);

        MaxAggregationBuilder ab = AggregationBuilders.max("agg").field(UPDATEDATE_FIELD);
        srb.addAggregation(ab);

        LOGGER.debug("Elasticsearch readUpdateMax typeIds [{}] - [{}]", (typeIdStrings.length == 0 ? "" : typeIdStrings), srb.toString());
        response = srb.execute().actionGet();
        response.getHits();

        Max agg = response.getAggregations().get("agg");
        double value = agg.getValue();
        return new Date((long) value);

    }

    /**
     * Define the ElasticGrouping
     */
    private static class ElasticGrouping<T> extends AbstractGrouping<T> {

        private final long count;

        public ElasticGrouping(List<Object> keys, Query<T> query, String[] fields, long count) {
            super(keys, query, fields);
            this.count = count;
        }

        // --- AbstractGrouping support ---

        @Override
        protected Aggregate createAggregate(String field) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getCount() {
            return count;
        }
    }

    /**
     * Get cached version of the indexName using the typeId
     */
    private String getElasticIndexName(UUID typeId) {

        try {
                return INDEXTYPEID_CACHE.get(typeId);
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Elasticsearch getElasticIndexName Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        return typeId.toString().replaceAll("-", "");
    }

    /**
     * Setup the index and cache it - especially useful in RAW mode
     */
    private void checkIndexes(String[] indexNames) {

        try {
            for (String newIndexname : indexNames) {
                IndexKey index = new IndexKey();
                index.setNodeSettings(this.nodeSettings);
                index.setClusterNodes(this.clusterNodes);
                index.setIndexId(newIndexname);
                index.setShardsMax(this.shardsMax);
                CREATE_INDEX_CACHE.get(index);
            }
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Elasticsearch checkIndexes Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
    }

    /**
     * Is you have the Group, check for SparseSet match and return false if it matches
     *
     * @see #isJsonState
     */
    private boolean isJsonGroup(Set<String> groups) {
        if (this.dataTypesRaw == null || ObjectUtils.isBlank(this.dataTypesRaw)) {
            return this.defaultDataFieldType.equals(JSON_DATAFIELD_TYPE);
        }
        for (String g : groups) {
            if (dataTypesRawSparseSet != null && dataTypesRawSparseSet.contains(g)) {
                return false;
            }
        }
        return this.defaultDataFieldType.equals(JSON_DATAFIELD_TYPE);
    }

    /**
     * Indicates if the field in Elastic should be writting in Json (Stringized) or Raw (Object) in data field
     *
     *  @see #isJsonGroup
     */
    private boolean isJsonState(State stateValue) {
        if (this.dataTypesRaw == null || ObjectUtils.isBlank(this.dataTypesRaw)) {
            return this.defaultDataFieldType.equals(JSON_DATAFIELD_TYPE);
        }
        if (stateValue != null) {
            if (stateValue.getType() != null) {
                if (stateValue.getType().getGroups() != null) {
                    return isJsonGroup(stateValue.getType().getGroups());
                }
            }
        }
        return this.defaultDataFieldType.equals(JSON_DATAFIELD_TYPE);
    }

    /**
     * Read partial results from Elastic - convert Query to SearchRequestBuilder
     *
     * @see #getSubQueryResolveLimit()
     * @see #predicateToSortBuilder(List, QueryBuilder, Query, SearchRequestBuilder, String[])
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> PaginatedResult<T> readPartial(Query<T> query, long offset, int limit) {
        LOGGER.debug("Elasticsearch PaginatedResult readPartial query.getPredicate() [{}]", query.getPredicate());

        List<T> items = new ArrayList<>();
        TransportClient client = openConnection();
        if (client == null || !isAlive(client)) {
            LOGGER.info("readPartial could not openConnection / not Alive");
            return new PaginatedResult<>(offset, limit, 0, items);
        }

        Set<UUID> typeIds = new HashSet<>();

        if (!query.isFromAll()) {
            typeIds = query.getConcreteTypeIds(this);
            if (typeIds.size() == 0) {
                return new PaginatedResult<>(offset, limit, 0, items);
            }
        }

        if (query.getGroup() != null && typeIds.size() == 0) {
            // should limit by the type
            LOGGER.debug("Elasticsearch PaginatedResult readPartial the call is to limit by from() but did not load typeIds! [{}]", query.getGroup());
        }
        String[] typeIdStrings = typeIds.size() == 0
                ? new String[]{}
                : typeIds.stream().map(UUID::toString).toArray(String[]::new);

        List<String> indexNames = new ArrayList<>();
        for (UUID u : typeIds) {
            indexNames.add(getIndexName() + getElasticIndexName(u));
        }
        String[] indexIdStrings = indexNames.toArray(new String[0]);
        checkIndexes(indexIdStrings);

        SearchResponse response;
        QueryBuilder qb = predicateToQueryBuilder(query.getPredicate(), query);
        SearchRequestBuilder srb;

        if (typeIds.size() > 0) {
            srb = client.prepareSearch(indexIdStrings)
                    .setFetchSource(!query.isReferenceOnly())
                    .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
            srb.setTypes(typeIdStrings);
        } else {
            srb = client.prepareSearch(getIndexName() + "*")
                    .setFetchSource(!query.isReferenceOnly())
                    .setTimeout(query.getTimeout() != null && query.getTimeout() > 0 ? TimeValue.timeValueMillis(query.getTimeout().longValue()) : TimeValue.timeValueMillis(this.searchTimeout));
        }

        srb.setQuery(qb)
                .setFrom((int) offset)
                .setSize(limit);
        for (SortBuilder sb : predicateToSortBuilder(query.getSorters(), qb, query, srb, typeIdStrings)) {
            srb.addSort(sb);
        }

        srb.setTrackScores(true);

        try {
            LOGGER.debug(
                    String.format("Elasticsearch srb index [%s] typeIds [%s] - [%s]",
                            (indexIdStrings.length == 0 ? getIndexName() + "*" : indexIdStrings),
                            (typeIdStrings.length == 0 ? "" : typeIdStrings),
                            srb.toString()));
            response = srb.execute().actionGet();
            SearchHits hits = response.getHits();
            Float maxScore = hits.getMaxScore();
            if (maxScore != null && maxScore.equals(Float.NaN)) {
                maxScore = null;
            }

            for (SearchHit hit : hits.getHits()) {
                items.add(createSavedObjectWithHit(hit, query, maxScore));
            }

            LOGGER.debug("Elasticsearch PaginatedResult readPartial hits [{} of {} totalHits]", items.size(), hits.getTotalHits());

            return new PaginatedResult<>(offset, limit, hits.getTotalHits(), items);

        } catch (Exception error) {
            LOGGER.warn(
                    String.format("index [%s] typeIds [%s] - [%s] readPartial threw Exception [%s: %s]",
                            (indexIdStrings.length == 0 ? getIndexName() + "*" : indexIdStrings),
                            (typeIdStrings.length == 0 ? "" : typeIdStrings),
                            srb.toString(),
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
            throw error;
        }
    }

    /**
     * Take the saved object and convert to objectState and swap it
     */
    private <T> T createSavedObjectWithHit(SearchHit hit, Query<T> query, Float maxScore) {
        T object = createSavedObject(hit.getType(), hit.getId(), query);

        State objectState = State.getInstance(object);

        if (!objectState.isReferenceOnly()) {
            Map<String, Object> source = hit.getSource();

            if (source == null || ObjectUtils.isBlank(source.get(DATA_FIELD))) {
                Object original = objectState.getDatabase().readFirst(Query.from(Object.class).where("_id = ?", objectState.getId()));
                if (original != null) {
                    objectState.setValues(State.getInstance(original).getSimpleValues());
                }

            } else {
                Map<String, Object> values;
                if (isJsonState(objectState)) {
                    String data = (String) source.get(DATA_FIELD);
                    //noinspection unchecked
                    values = (Map<String, Object>) ObjectUtils.fromJson(data);
                } else {
                    //noinspection unchecked
                    values = (Map<String, Object>) source.get(DATA_FIELD);
                }
                objectState.setValues(values);
            }
        }

        Map<String, Object> extras = objectState.getExtras();
        Float score = hit.getScore();
        if (score == null || (score != null && score.equals(Float.NaN))) {
            score = 1.0f;   // constant Score
        }
        extras.put(SCORE_EXTRA, score);

        if (maxScore != null && maxScore != 0.0f) {
            extras.put(NORMALIZED_SCORE_EXTRA, score / maxScore);
        }

        return swapObjectType(query, object);
    }

    /**
     * Check special fields for Elastic - sorting
     */
    private final Map<Query.MappedKey, String> specialSortFields; {
        Map<Query.MappedKey, String> m = new HashMap<>();
        m.put(Query.MappedKey.ID, IDS_FIELD);
        m.put(Query.MappedKey.TYPE, TYPE_ID_FIELD);
        m.put(Query.MappedKey.ANY, ANY_FIELD);
        specialSortFields = m;
    }

    private final Map<Query.MappedKey, String> specialRangeFields; {
        Map<Query.MappedKey, String> m = new HashMap<>();
        m.put(Query.MappedKey.ID, IDS_FIELD);
        m.put(Query.MappedKey.TYPE, TYPE_ID_FIELD);
        m.put(Query.MappedKey.ANY, ANY_FIELD);
        specialRangeFields = m;
    }

    /**
     * Check special fields for Elastic - non sorting
     */
    private final Map<Query.MappedKey, String> specialFields; {
        Map<Query.MappedKey, String> m = new HashMap<>();
        m.put(Query.MappedKey.ID, ID_FIELD);
        m.put(Query.MappedKey.TYPE, TYPE_ID_FIELD);
        m.put(Query.MappedKey.ANY, ANY_FIELD);
        specialFields = m;
    }

    /**
     * Convert key to Query
     */
    private static String convertKeyToQuery(String key) {
        if (key.equals(IDS_FIELD)) {
            return ID_FIELD;
        } else {
            return key;
        }
    }

    /**
     * Denormalize the key or return Query.NoFieldException
     *
     * @return Query.MappedKey {@code null} can be returned
     * @throws Query.NoFieldException No field matches this key
     */
    private Query.MappedKey mapFullyDenormalizedKey(Query<?> query, String key) {

        Query.MappedKey mappedKey = query.mapDenormalizedKey(getEnvironment(), convertKeyToQuery(key));
        if (mappedKey == null) {
            return null;
        }
        if (isReference(key, query)) {
            return mappedKey;
        } else if (mappedKey.hasSubQuery()) {
            throw new Query.NoFieldException(query.getGroup(), convertKeyToQuery(key));
        }
        return mappedKey;
    }

    /**
     * Look for field name recursively in Elastic
     * Return true if found it, else false
     *
     * @see #checkElasticMappingField
     */
    private boolean findElasticMap(Map<String, Object> properties, List<String> key, int length) {
        if (properties != null) {
            if (length < key.size()) {
                /* check fields separate */
                if (properties.get("fields") != null) {
                    if (properties.get("fields") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fields = (Map<String, Object>) properties.get("fields");
                        if (fields.get(key.get(length)) != null) {
                            if (length == key.size() - 1) {
                                return true;
                            }
                        }
                    }
                }
                if (properties.get("properties") != null) {
                    if (properties.get("properties") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> p = (Map<String, Object>) properties.get("properties");
                        return findElasticMap(p, key, length);
                    }
                } else if (properties.get(key.get(length)) != null) {
                    if (length == key.size() - 1) {
                        return true;
                    }
                    if (properties.get(key.get(length)) instanceof Map) {
                        //noinspection unchecked
                        return findElasticMap((Map<String, Object>) properties.get(key.get(length)), key, length + 1);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check types for field in Elastic mapping. Note that the field must be inserted not null to show up.
     *
     * @return true if the field exists
     * @throws IOException the Elastic could fail on getting _mapping on the type
     * @see #findElasticMap
     */
    private boolean checkElasticMappingField(String[] typeIds, String field) throws IOException {

        if (typeIds != null && typeIds.length > 0) {
            GetMappingsResponse response = client.admin().indices()
                    .prepareGetMappings(indexName + "*")
                    .setTypes(typeIds)
                    .execute().actionGet();

            if (response != null && response.getMappings() != null) {
                for (String typeId : typeIds) {
                    LOGGER.debug("Checking mapping for {}, {}", indexName + typeId.replaceAll("-", ""), typeId);
                    if (response.getMappings().get(indexName + typeId.replaceAll("-", "")) != null
                            && response.getMappings().get(indexName + typeId.replaceAll("-", "")).get(typeId) != null) {
                        Map<String, Object> source = response.getMappings().get(indexName + typeId.replaceAll("-", "")).get(typeId).sourceAsMap();
                        if (source.get("properties") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> properties = (Map<String, Object>) source.get("properties");
                            List<String> items = Arrays.asList(field.split("\\."));
                            if (!findElasticMap(properties, items, 0)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Interrogate the field and convert to Normal field
     */
    private String getField(String key) {
        if (key.endsWith("." + RAW_FIELD)) {
            key = key.substring(0, key.length() - ("." + RAW_FIELD).length());
        } else if (key.endsWith("." + LOCATION_FIELD)) {
            key = key.substring(0, key.length() - ("." + LOCATION_FIELD).length());
        } else if (key.endsWith("." + MATCH_FIELD)) {
            key = key.substring(0, key.length() - ("." + MATCH_FIELD).length());
        } else if (key.endsWith("." + SUGGEST_FIELD)) {
            key = key.substring(0, key.length() - ("." + SUGGEST_FIELD).length());
        } else if (key.endsWith("." + REGION_FIELD)) {
            key = key.substring(0, key.length() - ("." + REGION_FIELD).length());
        } else if (key.endsWith("." + BOOLEAN_FIELD)) {
            key = key.substring(0, key.length() - ("." + BOOLEAN_FIELD).length());
        } else if (key.endsWith("." + DATE_FIELD)) {
            key = key.substring(0, key.length() - ("." + DATE_FIELD).length());
        } else if (key.endsWith("." + NUMBER_FIELD)) {
            key = key.substring(0, key.length() - ("." + NUMBER_FIELD).length());
        } else if (key.endsWith("." + STRING_FIELD)) {
            key = key.substring(0, key.length() - ("." + STRING_FIELD).length());
        }
        return key;
    }

    /**
     * When querying from Elastic this appends type to the string for Elastic
     */
    private String addQueryFieldType(String internalType, String key, boolean isExact) {
        if (IDS_FIELD.equals(key) || ID_FIELD.equals(key) || TYPE_ID_FIELD.equals(key) || (internalType == null)) {
            return key;
        } else {
            if (isExact && (ObjectField.UUID_TYPE.equals(internalType) || ObjectField.TEXT_TYPE.equals(internalType))) {
                if (key.endsWith("." + RAW_FIELD)) {
                    return key;
                } else {
                    return key + "." + RAW_FIELD;
                }
            } else if (ObjectField.RECORD_TYPE.equals(internalType)) {
                if (key.endsWith("." + RAW_FIELD)) {
                    return key;
                } else {
                    return key + "." + RAW_FIELD;
                }
            } else if (ObjectField.LOCATION_TYPE.equals(internalType)) {
                if (key.endsWith("." + LOCATION_FIELD)) {
                    return key;
                } else {
                    return key + "." + LOCATION_FIELD;
                }
            } else if (ObjectField.REGION_TYPE.equals(internalType)) {
                if (key.endsWith("." + REGION_FIELD)) {
                    return key;
                } else {
                    return key + "." + REGION_FIELD;
                }
            } else if (ObjectField.BOOLEAN_TYPE.equals(internalType)) {
                if (key.endsWith("." + BOOLEAN_FIELD)) {
                    return key;
                } else {
                    return key + "." + BOOLEAN_FIELD;
                }
            } else if (ObjectField.DATE_TYPE.equals(internalType)) {
                if (key.endsWith("." + DATE_FIELD)) {
                    return key;
                } else {
                    return key + "." + DATE_FIELD;
                }
            } else if (ObjectField.NUMBER_TYPE.equals(internalType)) {
                if (key.endsWith("." + NUMBER_FIELD)) {
                    return key;
                } else {
                    return key + "." + NUMBER_FIELD;
                }
            } else {
                return key + "." + STRING_FIELD;
            }
        }
    }

    /**
     * When indexing to Elastic this appends type to the string for Elastic
     * file, uri, url are Strings
     */
    private String addIndexFieldType(String internalType, String key, Object value) {
        //LOGGER.info("key: [{}]", key);
        if (IDS_FIELD.equals(key) || ID_FIELD.equals(key) || TYPE_ID_FIELD.equals(key) || (internalType == null)) {
            return key;
        } else {
            //LOGGER.info("key: [{}], internalType [{}]", key, internalType);
            // might want to use string for ANY_TYPE for value
            if (ObjectField.ANY_TYPE.equals(internalType)) {
                if (value instanceof Boolean) {
                    if (key.endsWith("." + BOOLEAN_FIELD)) {
                        return key;
                    } else {
                        return key + "." + BOOLEAN_FIELD;
                    }
                } else if (value instanceof Date) {
                    if (key.endsWith("." + DATE_FIELD)) {
                        return key;
                    } else {
                        return key + "." + DATE_FIELD;
                    }
                } else if (value instanceof Number) {
                    if (key.endsWith("." + NUMBER_FIELD)) {
                        return key;
                    } else {
                        return key + "." + NUMBER_FIELD;
                    }
                } else {
                    if (key.endsWith("." + STRING_FIELD)) {
                        return key;
                    } else {
                        return key + "." + STRING_FIELD;
                    }
                }
            } else if (ObjectField.UUID_TYPE.equals(internalType) || ObjectField.TEXT_TYPE.equals(internalType)) {
                if (key.endsWith("." + "raw")) {
                    return key.substring(0, key.length() - ("." + "raw").length());
                } else {
                    if (key.endsWith("." + STRING_FIELD)) {
                        return key;
                    } else {
                        return key + "." + STRING_FIELD;
                    }
                }
            } else if (ObjectField.RECORD_TYPE.equals(internalType)) {
                // back to STRING_FIELD
                if (key.endsWith("." + "raw")) {
                    return key.substring(0, key.length() - ("." + "raw").length());
                } else {
                    return key + "." + STRING_FIELD;
                }
            } else if (ObjectField.LOCATION_TYPE.equals(internalType)) {
                if (key.endsWith("." + LOCATION_FIELD) || key.endsWith(".x") || key.endsWith(".y")) {
                    return key;
                } else {
                    return key + "." + LOCATION_FIELD;
                }
            } else if (ObjectField.REGION_TYPE.equals(internalType)) {
                if (key.endsWith("." + REGION_FIELD)) {
                    return key;
                } else {
                    return key + "." + REGION_FIELD;
                }
            } else if (ObjectField.BOOLEAN_TYPE.equals(internalType)) {
                if (key.endsWith("." + BOOLEAN_FIELD)) {
                    return key;
                } else {
                    return key + "." + BOOLEAN_FIELD;
                }
            } else if (ObjectField.DATE_TYPE.equals(internalType)) {
                if (key.endsWith("." + DATE_FIELD)) {
                    return key;
                } else {
                    return key + "." + DATE_FIELD;
                }
            } else if (ObjectField.NUMBER_TYPE.equals(internalType)) {
                if (key.endsWith("." + NUMBER_FIELD)) {
                    return key;
                } else {
                    return key + "." + NUMBER_FIELD;
                }
            } else {
                if (key.endsWith("." + STRING_FIELD)) {
                    return key;
                } else {
                    return key + "." + STRING_FIELD;
                }
            }
        }
    }

    /**
     * Interrogate the field type and convert to Elasticsearch for unmapped sort types
     */
    private String getFieldType(String key, Query<?> query) {
        if (key.equals(UID_FIELD) || key.equals(ID_FIELD) || key.equals(TYPE_ID_FIELD)) {
            return "keyword";
        }
        key = getField(key);

        Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, key);
        String elasticField = specialSortFields.get(mappedKey);

        if (elasticField == null) {
            if (mappedKey != null) {
                String internalType = mappedKey.getInternalType();
                if (internalType != null) {
                    if (ObjectField.DATE_TYPE.equals(internalType)) {
                        return "long";
                    } else if (ObjectField.TEXT_TYPE.equals(internalType)) {
                        return "keyword";
                    } else if (ObjectField.BOOLEAN_TYPE.equals(internalType)) {
                        return "boolean";
                    } else if (ObjectField.NUMBER_TYPE.equals(internalType)) {
                        //NUMBER_TYPE could be several Elasticsearch types - long, integer, short, byte, double, float
                        // return double since it is a superset
                        return "double";
                    } else if (ObjectField.REGION_TYPE.equals(internalType)) {
                        return "geo_shape";
                    } else if (ObjectField.LOCATION_TYPE.equals(internalType)) {
                        return "geo_point";
                    } else if (ObjectField.UUID_TYPE.equals(internalType)) {
                        return "keyword";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Build a list of Terms Order for Agg based on Elastic
     */
    private List<Terms.Order> predicateToSortBuilderGrouping(List<Sorter> sorters, Query<?> query, String aggKey) {
        List<Terms.Order> list = new ArrayList<>();
        if (sorters == null || sorters.size() == 0) {
            list.add(Terms.Order.count(false));
        } else {

            aggKey = getField(aggKey);
            for (Sorter sorter : sorters) {
                String operator = sorter.getOperator();
                if (Sorter.ASCENDING_OPERATOR.equals(operator) || Sorter.DESCENDING_OPERATOR.equals(operator)) {
                    boolean isAscending = Sorter.ASCENDING_OPERATOR.equals(operator);
                    String queryKey = (String) sorter.getOptions().get(0);

                    Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, queryKey);
                    String elasticField = specialSortFields.get(mappedKey);

                    Query.MappedKey mappedAggKey = mapFullyDenormalizedKey(query, aggKey);
                    String elasticAggField = specialSortFields.get(mappedAggKey);

                    if (elasticField == null && mappedKey != null) {
                        elasticField = mappedKey.getIndexKey(null);
                    }

                    if (elasticAggField == null && mappedAggKey != null) {
                        elasticAggField = mappedAggKey.getIndexKey(null);
                    }

                    if (elasticField == null || elasticAggField == null) {
                        throw new UnsupportedIndexException(this, queryKey);
                    }
                    if (elasticAggField.equals(elasticField)) {
                        list.add(isAscending ? Terms.Order.term(true) : Terms.Order.term(false));
                    } else {
                        throw new IllegalArgumentException(operator + " needs to be same " + elasticAggField + " != " + elasticField);
                    }
                } else {
                    throw new UnsupportedOperationException(operator + " not supported");
                }
            }
        }
        return list;
    }

    /**
     * Build a list of SortBuilder sorters based on Elastic
     */
    private List<SortBuilder> predicateToSortBuilder(List<Sorter> sorters, QueryBuilder orig, Query<?> query, SearchRequestBuilder srb, String[] typeIds) {
        List<SortBuilder> list = new ArrayList<>();
        if (sorters == null || sorters.size() == 0) {
            list.add(new ScoreSortBuilder());
            list.add(new FieldSortBuilder(TYPE_ID_FIELD).order(ASC).unmappedType("keyword"));
        } else {
            List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();

            for (Sorter sorter : sorters) {
                String operator = sorter.getOperator();
                if (Sorter.ASCENDING_OPERATOR.equals(operator) || Sorter.DESCENDING_OPERATOR.equals(operator)) {
                    boolean isAscending = Sorter.ASCENDING_OPERATOR.equals(operator);
                    String queryKey = (String) sorter.getOptions().get(0);

                    String elasticField = convertAscendingElasticField(queryKey, query, typeIds);

                    if (elasticField == null) {
                        throw new UnsupportedIndexException(this, queryKey);
                    }
                    String unmappedTypeString = getFieldType(queryKey, query);
                    FieldSortBuilder fs = new FieldSortBuilder(elasticField).order(isAscending ? ASC : DESC);
                    if (unmappedTypeString != null) {
                        fs.unmappedType(unmappedTypeString);
                    }
                    list.add(fs);
                } else if (Sorter.OLDEST_OPERATOR.equals(operator) || Sorter.NEWEST_OPERATOR.equals(operator)) {
                    if (sorter.getOptions().size() < 2) {
                        throw new IllegalArgumentException(operator + " requires Date field");
                    }
                    boolean isOldest = Sorter.OLDEST_OPERATOR.equals(operator);
                    String queryKey = (String) sorter.getOptions().get(1);
                    Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, queryKey);
                    String elasticField = specialSortFields.get(mappedKey);

                    if (elasticField == null && mappedKey != null) {
                        queryKey = mappedKey.getIndexKey(null);
                        String internalType = mappedKey.getInternalType();
                        if (internalType != null) {
                            if (ObjectField.DATE_TYPE.equals(internalType)) {
                                elasticField = addQueryFieldType(internalType, queryKey, true);
                            } else {
                                throw new IllegalArgumentException();
                            }
                        }
                    }

                    if (elasticField == null) {
                        throw new UnsupportedIndexException(this, queryKey);
                    }

                    float boost = ObjectUtils.to(float.class, sorter.getOptions().get(0));
                    if (boost == 0) {
                        boost = 1.0f;
                    }
                    boost = .1f * boost;

                    long scale = MILLISECONDS_IN_5YEAR; // 5 years scaling
                    if (!isOldest) {
                        filterFunctionBuilders.add(
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(ScoreFunctionBuilders.exponentialDecayFunction(elasticField, new Date().getTime(), scale, 0, .1).setWeight(boost))
                        );
                        // Solr: recip(x,m,a,b) implementing a/(m*x+b)
                        // boostFunctionBuilder.append(String.format("{!boost b=recip(ms(NOW/HOUR,%s),3.16e-11,%s,%s)}", elasticField, boost, boost));
                    } else {
                        filterFunctionBuilders.add(
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(ScoreFunctionBuilders.exponentialDecayFunction(elasticField, DateUtils.addYears(new java.util.Date(), -5).getTime(), scale, 0, .1).setWeight(boost))
                        );
                        // Solr: linear(x,2,4) returns 2*x+4
                        // boostFunctionBuilder.append(String.format("{!boost b=linear(ms(NOW/HOUR,%s),3.16e-11,%s)}", elasticField, boost));
                    }

                } else if (Sorter.FARTHEST_OPERATOR.equals(operator) || Sorter.CLOSEST_OPERATOR.equals(operator)) {
                    if (sorter.getOptions().size() < 2) {
                        throw new IllegalArgumentException(operator + " requires Location");
                    }
                    boolean isClosest = Sorter.CLOSEST_OPERATOR.equals(operator);
                    String queryKey = (String) sorter.getOptions().get(0);

                    String elasticField = convertFarthestElasticField(queryKey, query, typeIds);

                    if (!(sorter.getOptions().get(1) instanceof Location)) {
                        throw new IllegalArgumentException(operator + " requires Location");
                    }
                    Location sort = (Location) sorter.getOptions().get(1);
                    list.add(new GeoDistanceSortBuilder(elasticField, new GeoPoint(sort.getX(), sort.getY()))
                            .order(isClosest ? SortOrder.ASC : SortOrder.DESC));
                } else if (Sorter.RELEVANT_OPERATOR.equals(operator)) {
                    Predicate sortPredicate;
                    Object predicateObject = sorter.getOptions().get(1);
                    Object boostObject = sorter.getOptions().get(0);
                    String boostStr = boostObject.toString();
                    Float boost = Float.valueOf(boostStr);
                    if (predicateObject instanceof Predicate) {
                        sortPredicate = (Predicate) predicateObject;
                        filterFunctionBuilders.add(
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                        predicateToQueryBuilder(sortPredicate, query),
                                        weightFactorFunction(boost)));
                    } else {
                        list.add(new ScoreSortBuilder());
                    }
                } else {
                    throw new UnsupportedOperationException(operator + " not supported");
                }
            }
            if (filterFunctionBuilders.size() > 0) {
                list.add(new ScoreSortBuilder());
                FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
                for (int i = 0; i < filterFunctionBuilders.size(); i++) {
                    functions[i] = filterFunctionBuilders.get(i);
                }
                orig = QueryBuilders.functionScoreQuery(orig, functions)
                        .boostMode(CombineFunction.MULTIPLY)
                        .boost(1.0f)
                        .maxBoost(1000.0f);
                srb.setQuery(orig);
            }
        }
        return list;

    }

    /**
     * Handy to see if the object is a reference and not Embedded
     * Note: {@code ((Query.StandardMappedKey) mappedKey).hasSubQuery()} does not check field independently for _ref and not embedded
     *
     * @return if the queryKey is a reference and not an normal Record Object or Embedded object
     */
    private boolean isReference(String queryKey, Query<?> query) {
        try {
            Query.MappedKey mappedKey = query.mapDenormalizedKey(getEnvironment(), convertKeyToQuery(queryKey));
            if (mappedKey != null) {
                if (mappedKey.hasSubQuery()) {
                    return true;
                }

                if (mappedKey.getField() != null) {
                    if (mappedKey.getField().getState() != null && mappedKey.getField().getState() instanceof Map) {
                        Map<String, Object> itemMap = mappedKey.getField().getState().getSimpleValues();
                        if (itemMap != null) {
                            if (itemMap.get("valueTypes") != null && itemMap.get("valueTypes") instanceof List) {
                                if (itemMap.get("isEmbedded") != null && !((Boolean) itemMap.get("isEmbedded"))) {
                                    List l = (List) itemMap.get("valueTypes");
                                    if (l.size() == 1 && l.get(0) != null && l.get(0) instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> o = (Map<String, Object>) l.get(0);
                                        if (o.get(StateSerializer.REFERENCE_KEY) != null) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * For ascending sort take queryKey and return the Elastic equivalent for text, location and region
     *
     * @throws IllegalArgumentException  the argument is illegal
     * @throws UnsupportedIndexException the mapping not setup properly
     */
    private <T> String convertAscendingElasticField(String queryKey, Query<T> query, String[] typeIds) {
        String elasticField;

        Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, queryKey);
        if (mappedKey != null && mappedKey.hasSubQuery()) {
            throw new IllegalArgumentException(queryKey + " cannot sort subQuery (create denormalized fields) on Ascending/Descending");
        }

        elasticField = specialSortFields.get(mappedKey);

        /* skip for special */
        if (elasticField == null) {
            queryKey = mappedKey.getIndexKey(null);
            String internalType = mappedKey.getInternalType();
            if (internalType != null) {
                if (ObjectField.TEXT_TYPE.equals(internalType) || ObjectField.UUID_TYPE.equals(internalType)) {
                    elasticField = addRaw(queryKey);
                } else if (ObjectField.LOCATION_TYPE.equals(internalType)) {
                    elasticField = queryKey + "." + LOCATION_FIELD;
                    // not sure what to do with lat,long and sort?
                    throw new IllegalArgumentException(elasticField + " cannot sort Location on Ascending/Descending");
                } else if (ObjectField.REGION_TYPE.equals(internalType)) {
                    elasticField = queryKey + "." + REGION_FIELD;
                    throw new IllegalArgumentException(elasticField + " cannot sort GeoJSON in Elastic Search");
                } else {
                    elasticField = addQueryFieldType(internalType, queryKey, true);
                }
            }
            if (elasticField == null) {
                elasticField = queryKey;
            }
        }
        return elasticField;
    }

    /**
     * For Farthest / Nearest sort get the elasticField
     */
    private <T> String convertFarthestElasticField(String queryKey, Query<T> query, String[] typeIds) {
        String elasticField;

        Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, queryKey);

        if (mappedKey != null && mappedKey.hasSubQuery()) {
            throw new Query.NoFieldException(query.getGroup(), queryKey + " cannot sort subQuery (create denormalized fields) on Nearest/Farthest");
        }

        elasticField = specialSortFields.get(mappedKey);
        if (elasticField == null) {
            if (mappedKey != null) {
                elasticField = mappedKey.getIndexKey(null);
                String internalType = mappedKey.getInternalType();
                if (internalType != null) {
                    if (ObjectField.NUMBER_TYPE.equals(internalType)) {
                        throw new IllegalArgumentException(elasticField + " cannot sort Number Closest/Farthest");
                    }
                    if (ObjectField.LOCATION_TYPE.equals(internalType)) {
                        elasticField = elasticField + "." + LOCATION_FIELD;
                    }
                    if (ObjectField.REGION_TYPE.equals(internalType)) {
                        throw new IllegalArgumentException(elasticField + " cannot sort GeoJSON Closest/Farthest");
                    }
                    if (ObjectField.UUID_TYPE.equals(internalType)) {
                        throw new IllegalArgumentException(elasticField + " cannot sort UUID Closest/Farthest");
                    }
                    if (ObjectField.TEXT_TYPE.equals(internalType)) {
                        throw new IllegalArgumentException(elasticField + " cannot sort Location on text Closest/Farthest");
                    }
                }
            } else {
                throw new Query.NoFieldException(query.getGroup(), queryKey);
            }
            if (elasticField == null) {
                elasticField = queryKey;
            }
        }

        return elasticField;
    }

    /**
     * A reference is difficult to join in Elastic. This returns a list of Ids so you can join on next level of reference
     * In reality it is a join when you have a subQuery.
     *
     * @throws IllegalArgumentException the argument is illegal
     */
    private <T> List<String> referenceSwitcher(String key, Query<T> query) {
        String[] keyArr = key.split("/");
        List<String> allids = new ArrayList<>();
        List<?> list = new ArrayList<T>();
        // Go until last - since the user might want something besides != missing...
        if (keyArr.length > 0) {
            for (int i = 0; i < keyArr.length - 1; i++) {
                if (isReference(keyArr[i], query)) {
                    PaginatedResult<?> result;
                    if (allids.size() == 0) {
                        result = Query.from(query.getObjectClass()).where(keyArr[i] + " != missing").select(0, SUBQUERY_MAX_ROWS);
                    } else {
                        result = Query.from(query.getObjectClass()).where(keyArr[i] + " != missing").and("_id contains ?", allids).select(0, SUBQUERY_MAX_ROWS);
                    }

                    int count = (int) result.getCount();
                    list = result.getItems();
                    if (list.size() > (SUBQUERY_MAX_ROWS - 1)) {
                        LOGGER.warn("reference join in Elasticsearch is > {} which will limit results", (SUBQUERY_MAX_ROWS - 1));
                        throw new IllegalArgumentException(key + " / joins > " + (SUBQUERY_MAX_ROWS - 1) + " not allowed");
                    }
                    allids = new ArrayList<>();
                    for (int j = 0; j < list.size(); j++) {
                        if (list.get(j) instanceof Record) {
                            Map<String, Object> itemMap = ((Record) list.get(j)).getState().getSimpleValues(false);
                            if (itemMap.get(keyArr[i]) instanceof Map && itemMap.get(keyArr[i]) != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> o = (Map<String, Object>) itemMap.get(keyArr[i]);
                                if (o.get(StateSerializer.REFERENCE_KEY) != null) {
                                    allids.add((String) o.get(StateSerializer.REFERENCE_KEY));
                                }
                            } else if (itemMap.get(keyArr[i]) instanceof List && itemMap.get(keyArr[i]) != null) {
                                @SuppressWarnings("unchecked")
                                List<Object> subList = (List<Object>) itemMap.get(keyArr[i]);
                                for (Object sub : subList) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> s = (Map<String, Object>) sub;
                                    if (s.get(StateSerializer.REFERENCE_KEY) != null) {
                                        allids.add((String) s.get(StateSerializer.REFERENCE_KEY));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (list.size() > 0) {
            return allids;
        } else {
            return null;
        }
    }

    /**
     * For count we don't need to return any rows
     */
    @Override
    public long readCount(Query<?> query) {
        return readPartial(query, 0L, 0).getCount();
    }

    /**
     * Override to loop through at INITIAL_FETCH_SIZE at a time
     *
     * @param query Can't be {@code null}.
     * @see #readPartial(Query, long, int)
     * @see #getSubQueryResolveLimit()
     */
    @Override
    public <T> List<T> readAll(Query<T> query) {
        List<T> listFinal = new ArrayList<>();
        long row = 0L;
        boolean done = false;
        while (!done) {
            List<T> partial = readPartial(query, row, INITIAL_FETCH_SIZE).getItems();
            // most queries will be handled immediately
            if (partial.size() < INITIAL_FETCH_SIZE && row == 0) {
                return partial;
            }
            if (partial != null && partial.size() > 0) {
                listFinal.addAll(partial);
                row = row + partial.size();
            } else {
                done = true;
            }
        }
        return listFinal;
    }

    /**
     * Take circles and polygons and build a new GeoJson that works with Elastic
     */
    public String getGeoJson(List<Region.Circle> circles, Region.MultiPolygon polygons) {
        List<Map<String, Object>> features = new ArrayList<>();

        Map<String, Object> featureCollection = new HashMap<>();
        featureCollection.put("type", "geometrycollection");
        featureCollection.put("geometries", features);

        if (circles != null && circles.size() > 0) {

            for (Region.Circle circle : circles) {
                Map<String, Object> geometry = new HashMap<>();
                geometry.put("type", "circle");
                geometry.put("coordinates", circle.getGeoJsonArray().get(0)); // required for Elasticsearch
                geometry.put("radius", Math.ceil(circle.getRadius()) + "m");

                features.add(geometry);
            }
        }

        if (polygons != null && polygons.size() > 0) {
            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "multipolygon");
            geometry.put("coordinates", polygons);

            features.add(geometry);
        }

        return ObjectUtils.toJson(featureCollection);
    }

    /**
     * add Raw for fields that are not _ids, _id, _type
     */
    private static String addRaw(String query) {
        if (IDS_FIELD.equals(query) || ID_FIELD.equals(query) || TYPE_ID_FIELD.equals(query)) {
            return query;
        } else {
            if (query.endsWith("." + RAW_FIELD)) {
                return query;
            } else {
                return query + "." + RAW_FIELD;
            }
        }

    }

    /**
     * Get the QueryBuilder equalsany for each v. Splits out "Location" and "Region"
     */
    private QueryBuilder equalsAnyQuery(String simpleKey, String dotKey, String key, Query<?> query, Object v, ShapeRelation sr) {

        if (v == null) {
            return QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery());
        } else if (v instanceof String || isUUID(v)) {
            Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, key);
            String checkField = specialFields.get(mappedKey);
            if (checkField == null) {
                if (simpleKey != null) {
                    key = addRaw(key);
                } else {
                    if (isUUID(v) && isReference(dotKey, query)) {
                        return QueryBuilders.termQuery(addRaw(key), v);
                    } else {
                        String internalType = mappedKey.getInternalType();
                        if (!ObjectField.NUMBER_TYPE.equals(internalType)) {
                            key = addRaw(key);
                        }
                    }
                }
            }
        } else {
            Query.MappedKey mappedKey = mapFullyDenormalizedKey(query, key);
            String checkField = specialFields.get(mappedKey);
            if (checkField == null) {
                String internalType = mappedKey.getInternalType();
                if (v instanceof Boolean) {
                    if (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType)) {
                        throw new IllegalArgumentException(key + " boolean cannot be location");
                    } else if (internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                        throw new IllegalArgumentException(key + " boolean cannot be region");
                    } else {
                        key = addQueryFieldType(internalType, key, true);
                    }
                } else if (internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                    if (v instanceof Location) {
                        return QueryBuilders.boolQuery().must(geoShape(key + "." + REGION_FIELD, ((Location) v).getX(), ((Location) v).getY()));

                    } else if (v instanceof Region) {
                        // required to fix array issue on Circles and capitals
                        Region region = (Region) v;
                        String geoJson = getGeoJson(region.getCircles(), region.getPolygons());

                        String shapeJson = "{" + "\"shape\":" + geoJson + ", \"relation\": \"" + sr + "\"}";
                        String nameJson = "{" + "\"" + key + "." + REGION_FIELD + "\":" + shapeJson + "}";
                        String json = "{" + "\"geo_shape\":" + nameJson + "}";
                        return QueryBuilders.boolQuery().must(QueryBuilders.wrapperQuery(json));
                    }
                } else if (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType)) {
                    if (v instanceof Location) {
                        return QueryBuilders.boolQuery().must(QueryBuilders.termQuery(key + ".x", ((Location) v).getX()))
                                .must(QueryBuilders.termQuery(key + ".y", ((Location) v).getY()));
                    } else if (v instanceof Region) {
                        return QueryBuilders.geoDistanceQuery(key + "." + LOCATION_FIELD).point(((Region) v).getX(), ((Region) v).getY())
                                .distance(Region.degreesToKilometers(((Region) v).getRadius()), DistanceUnit.KILOMETERS);
                    }
                } else {
                    key = addQueryFieldType(internalType, key, true);
                }
            }
        }

        return QueryBuilders.termQuery(key, v);
    }

    /**
     * Get QueryBuilder for geoShape for intersects
     *
     * @see #predicateToQueryBuilder
     */
    private GeoShapeQueryBuilder geoShapeIntersects(String key, double x, double y) {
        try {
            return QueryBuilders
                    .geoShapeQuery(key, ShapeBuilders.newPoint(new Coordinate(x, y))).relation(ShapeRelation.INTERSECTS);
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("geoShapeIntersects threw Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        return null;
    }

    /**
     * Get QueryBuilder for geoShape for contains
     *
     * @see #predicateToQueryBuilder
     */
    private GeoShapeQueryBuilder geoShape(String key, double x, double y) {
        try {
            return QueryBuilders
                    .geoShapeQuery(key, ShapeBuilders.newPoint(new Coordinate(x, y))).relation(ShapeRelation.CONTAINS);
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("geoShape threw Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        return null;
    }

    /**
     * Get QueryBuilder for Circle for contains
     * Currently use geoShape higher level
     */
    private GeoShapeQueryBuilder geoCircle(String key, double x, double y, double r) {
        try {
            return QueryBuilders
                    .geoShapeQuery(key, ShapeBuilders.newCircleBuilder().center(x, y).radius(r, DistanceUnit.KILOMETERS)).relation(ShapeRelation.CONTAINS);
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("geoCircle threw Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
        }
        return null;
    }

    /**
     * For contains, add * for within strings
     */
    private static Object containsWildcard(String operator, Object value) {
        if (operator.equals(PredicateParser.CONTAINS_OPERATOR) && (value instanceof String)) {
            return "*" + value + "*";
        }
        return value;
    }

    /**
     * For "_Any" and Matches, switch UUID for value to uuidWord
     */
    private Object matchesAnyUUID(String operator, String key, Object value) {
        if (key.equals(ANY_FIELD)) {
            if (operator.equals(PredicateParser.STARTS_WITH_OPERATOR) || operator.equals(PredicateParser.MATCHES_ALL_OPERATOR) || operator.equals(PredicateParser.MATCHES_ANY_OPERATOR)) {
                UUID valueUuid = ObjectUtils.to(UUID.class, value);
                if (valueUuid != null) {
                    return uuidToWord(valueUuid);
                }
            }
        }
        return value;
    }

    /**
     * For Matches, add ".match" to the query, typeAhead add _suggest
     */
    private String matchesAnalyzer(String operator, String key, Set<UUID> typeIds) {
        if (key.endsWith("." + RAW_FIELD) || key.equals(ANY_FIELD)) {
            return key;
        } else if (operator.equals(PredicateParser.CONTAINS_OPERATOR)) {
            return key + "." + RAW_FIELD;
        } else if (operator.equals(PredicateParser.MATCHES_ALL_OPERATOR) || operator.equals(PredicateParser.MATCHES_ANY_OPERATOR)) {
            if (typeIds != null) {
                for (UUID typeId : typeIds) {
                    ObjectType type = ObjectType.getInstance(typeId);
                    if (type != null) {
                        if (type.getIndex(key) != null) {
                            if (type.getIndex(key).getName().equals(DEFAULT_SUGGEST_FIELD) || type.getIndex(key).getField().equals(DEFAULT_SUGGEST_FIELD)) {
                                return key + "." + SUGGEST_FIELD;
                            }
                        }
                        Map<String, List<String>> map = type.as(TypeModification.class).getTypeAheadFieldsMap();
                        if (map != null) {
                            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                                List<String> value = entry.getValue();
                                if (value.contains(key) || value.contains(type.getIndex(key).getField())) {
                                    return key + "." + SUGGEST_FIELD;
                                }
                            }
                        }
                    }
                }
            }
            return key + "." + MATCH_FIELD;
        }
        return key;
    }

    /**
     * This is the main method for querying Elastic. Converts predicate and query into QueryBuilder
     */
    QueryBuilder predicateToQueryBuilder(Predicate predicate, Query<?> query) {
        if (predicate == null) {
            return QueryBuilders.matchAllQuery();
        }
        LOGGER.debug("predicate: [{}]", predicate.toString());
        if (predicate instanceof CompoundPredicate) {
            CompoundPredicate compound = (CompoundPredicate) predicate;
            List<Predicate> children = compound.getChildren();

            switch (compound.getOperator()) {
                case PredicateParser.AND_OPERATOR:
                    return combine(compound.getOperator(), children, BoolQueryBuilder::must, (predicate1) -> predicateToQueryBuilder(predicate1, query));

                case PredicateParser.OR_OPERATOR:
                    return combine(compound.getOperator(), children, BoolQueryBuilder::should, (predicate1) -> predicateToQueryBuilder(predicate1, query));

                case PredicateParser.NOT_OPERATOR:
                    return combine(compound.getOperator(), children, BoolQueryBuilder::mustNot, (predicate1) -> predicateToQueryBuilder(predicate1, query));

                default:
                    break;
            }

        } else if (predicate instanceof ComparisonPredicate) {
            Set<UUID> typeIds = query.getConcreteTypeIds(this);

            ComparisonPredicate comparison = (ComparisonPredicate) predicate;
            String operator = comparison.getOperator();

            String queryKey = comparison.getKey();
            Query.MappedKey mappedKey = query.mapDenormalizedKey(getEnvironment(), queryKey);
            String elasticField;

            if (operator.equals(PredicateParser.EQUALS_ANY_OPERATOR)
                || operator.equals(PredicateParser.NOT_EQUALS_ALL_OPERATOR)) {
                elasticField = specialFields.get(mappedKey);
            } else {
                elasticField = specialRangeFields.get(mappedKey);
            }

            String elasticPostFieldExact = null;
            if (elasticField == null) {
                String internalType = mappedKey.getInternalType();
                if (internalType != null) {
                    elasticField = mappedKey.getIndexKey(null); // whole string
                    elasticPostFieldExact = addQueryFieldType(internalType, elasticField, true);
                }
            }

            if (elasticField == null) {
                throw new UnsupportedIndexException(this, queryKey);
            }

            String key = elasticField;
            if (elasticPostFieldExact == null) {
                elasticPostFieldExact = key;
            }

            String finalElasticPostFieldExact = elasticPostFieldExact;
            List<Object> values = comparison.getValues();

            String simpleKey = null;

            String pKey = queryKey;

            if (mappedKey != null) {
                // Elasticsearch like Solr does not support joins in 5.2. Might be memory issue and slow!
                // to do this requires query, take results and send to other query. Sample tests do this.

                Query<?> valueQuery = mappedKey.getSubQueryTypeWithComparison(comparison);

                List<String> ids = new ArrayList<>();
                if (valueQuery != null) {
                    for (Object item : readPartial(
                            valueQuery, 0, this.subQueryResolveLimit)
                            .getItems()) {
                        UUID u = State.getInstance(item).getId();
                        ids.add(u.toString());
                    }
                    LOGGER.debug("Get Sub Query: [{}] {}", valueQuery.getPredicate(), ids.size());
                } else {
                    // overwrite UUID and CLASS
                    values = comparison.resolveValues(this);
                }

                if (ids != null && ids.size() > 0) {
                    Query part1 = Query.from(query.getObjectClass()).where(convertKeyToQuery(elasticField) + " != missing");
                    Query part2 = Query.fromAll().where(convertKeyToQuery(elasticField) + " = ?", ids);
                    Query combinedParts = Query.fromAll().where(part1.getPredicate()).and(part2.getPredicate());
                    LOGGER.debug("returning subQuery ids [{}] [{}]", ids.size(), combinedParts.getPredicate());
                    return predicateToQueryBuilder(combinedParts.getPredicate(), query);
                } else if (queryKey.indexOf('/') != -1) {
                    // fields().size not the same as array in keys "/"
                    List<String> keyArr = Arrays.asList(queryKey.split("/"));
                    if (mappedKey.getFields().size() != keyArr.size()) {
                        simpleKey = mappedKey.getField().getInternalName();
                    }
                }
            }

            String internalType = null;

            switch (operator) {
                case PredicateParser.EQUALS_ANY_OPERATOR:
                case PredicateParser.NOT_EQUALS_ALL_OPERATOR:

                    String finalSimpleKey = simpleKey;
                    if (operator.equals(PredicateParser.EQUALS_ANY_OPERATOR)) {
                        return combine(operator, values, BoolQueryBuilder::should, v ->
                                v == null ? QueryBuilders.matchAllQuery()
                                : Query.MISSING_VALUE.equals(v) ? QueryBuilders.existsQuery(finalElasticPostFieldExact)
                                    : equalsAnyQuery(finalSimpleKey, key, key, query, v, ShapeRelation.WITHIN));
                    } else {
                        return combine(operator, values, BoolQueryBuilder::mustNot, v ->
                                v == null ? QueryBuilders.matchAllQuery()
                                : Query.MISSING_VALUE.equals(v) ? QueryBuilders.existsQuery(finalElasticPostFieldExact)
                                    : equalsAnyQuery(finalSimpleKey, key, key, query, v, ShapeRelation.WITHIN));
                    }

                case PredicateParser.LESS_THAN_OPERATOR :
                    mappedKey = mapFullyDenormalizedKey(query, key);
                    String checkField = specialRangeFields.get(mappedKey);
                    if (checkField == null) {
                        internalType = mappedKey.getInternalType();
                    }

                    for (Object v : values) {
                        if (v instanceof Boolean) {
                            throw new IllegalArgumentException(operator + " cannot be boolean");
                        }
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (v instanceof Location || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " cannot be location");
                        }
                        if (v != null && (internalType != null && ObjectField.REGION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " cannot be region");
                        }
                    }

                    final String intLtType = internalType;
                    return combine(operator, values, BoolQueryBuilder::must, v ->
                                    v == null ? QueryBuilders.matchAllQuery()
                                    : (isUUID(v) ? QueryBuilders.rangeQuery(addRaw(key)).lt(v)
                                    : (v instanceof Location ? QueryBuilders.boolQuery().must(QueryBuilders.rangeQuery(key + ".x").lt(((Location) v).getX()))
                                        .must(QueryBuilders.rangeQuery(key + ".y").lt(((Location) v).getY()))
                                    : QueryBuilders.rangeQuery(addQueryFieldType(intLtType, key, true)).lt(v))));

                case PredicateParser.LESS_THAN_OR_EQUALS_OPERATOR :
                    mappedKey = mapFullyDenormalizedKey(query, key);
                    checkField = specialRangeFields.get(mappedKey);
                    if (checkField == null) {
                        internalType = mappedKey.getInternalType();
                    }

                    for (Object v : values) {
                        if (v instanceof Boolean) {
                            throw new IllegalArgumentException(operator + " cannot be boolean");
                        }
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (v instanceof Location || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " cannot be location");
                        }
                        if (v != null && internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                            throw new IllegalArgumentException(operator + " cannot be region");
                        }
                    }

                    final String intLteType = internalType;
                    return combine(operator, values, BoolQueryBuilder::must, v ->
                            v == null ? QueryBuilders.matchAllQuery()
                            : (isUUID(v) ? QueryBuilders.rangeQuery(addRaw(key)).lte(v)
                            : (v instanceof Location
                                    ? QueryBuilders.boolQuery().must(QueryBuilders.rangeQuery(key + ".x").lte(((Location) v).getX()))
                                    .must(QueryBuilders.rangeQuery(key + ".y").lte(((Location) v).getY()))
                                    : QueryBuilders.rangeQuery(addQueryFieldType(intLteType, key, true)).lte(v))));

                case PredicateParser.GREATER_THAN_OPERATOR :
                    mappedKey = mapFullyDenormalizedKey(query, key);
                    checkField = specialRangeFields.get(mappedKey);
                    if (checkField == null) {
                        internalType = mappedKey.getInternalType();
                    }
                    for (Object v : values) {
                        if (v instanceof Boolean) {
                            throw new IllegalArgumentException(operator + " cannot be boolean");
                        }
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (v instanceof Location || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " cannot be location");
                        }
                        if (v != null && (internalType != null && ObjectField.REGION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " cannot be region");
                        }
                    }

                    final String intGtType = internalType;
                    return combine(operator, values, BoolQueryBuilder::must, v ->
                            v == null ? QueryBuilders.matchAllQuery()
                            : (isUUID(v) ? QueryBuilders.rangeQuery(addRaw(key)).gt(v)
                            : (v instanceof Location
                                    ? QueryBuilders.boolQuery().must(QueryBuilders.rangeQuery(key + ".x").gt(((Location) v).getX()))
                                    .must(QueryBuilders.rangeQuery(key + ".y").gt(((Location) v).getY()))
                                    : QueryBuilders.rangeQuery(addQueryFieldType(intGtType, key, true)).gt(v))));

                case PredicateParser.GREATER_THAN_OR_EQUALS_OPERATOR :

                    mappedKey = mapFullyDenormalizedKey(query, key);
                    checkField = specialRangeFields.get(mappedKey);
                    if (checkField == null) {
                        internalType = mappedKey.getInternalType();
                    }
                    for (Object v : values) {
                        if (v != null && v instanceof Boolean) {
                            throw new IllegalArgumentException(operator + " cannot be boolean");
                        }
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (v instanceof Location || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " cannot be location");
                        }
                        if (v != null && internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                            throw new IllegalArgumentException(key + " cannot be region");
                        }
                    }

                    final String intGteType = internalType;
                    return combine(operator, values, BoolQueryBuilder::must, v ->
                            v == null ? QueryBuilders.matchAllQuery()
                            : (isUUID(v) ? QueryBuilders.rangeQuery(addRaw(key)).gte(v)
                            : (v instanceof Location
                                    ? QueryBuilders.boolQuery().must(QueryBuilders.rangeQuery(key + ".x").gte(((Location) v).getX()))
                                    .must(QueryBuilders.rangeQuery(key + ".y").gte(((Location) v).getY()))
                                    : QueryBuilders.rangeQuery(addQueryFieldType(intGteType, key, true)).gte(v))));

                case PredicateParser.STARTS_WITH_OPERATOR :
                    mappedKey = mapFullyDenormalizedKey(query, key);
                    checkField = specialRangeFields.get(mappedKey);
                    if (checkField == null) {
                        internalType = mappedKey.getInternalType();
                    }

                    for (Object v : values) {
                        if (internalType != null && ObjectField.NUMBER_TYPE.equals(internalType)) {
                            throw new IllegalArgumentException(operator + " number not allowed");
                        }
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (internalType != null && ObjectField.UUID_TYPE.equals(internalType)) {
                            if (v == null || (v != null && v.equals(new UUID(0, 0)))) {
                                throw new IllegalArgumentException(operator + " UUID of null/0 not allowed");
                            }
                        }
                        if (v instanceof Location || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                            throw new IllegalArgumentException(operator + " location not allowed");
                        }
                        if (v != null && v instanceof Region) {
                            throw new IllegalArgumentException(operator + " region not allowed");
                        }
                    }
                    return combine(operator, values, BoolQueryBuilder::should, v ->
                            v == null ? QueryBuilders.matchAllQuery()
                            : checkField != null ? QueryBuilders.prefixQuery(key, String.valueOf(matchesAnyUUID(operator, key, v)))
                            : QueryBuilders.prefixQuery(key + "." + STRING_FIELD, String.valueOf(v)));

                case PredicateParser.CONTAINS_OPERATOR :
                case PredicateParser.MATCHES_ANY_OPERATOR :
                    if (!ANY_FIELD.equals(key)) {
                        mappedKey = mapFullyDenormalizedKey(query, key);
                        checkField = specialFields.get(mappedKey);
                        if (checkField == null) {
                            internalType = mappedKey.getInternalType();
                        }
                    }

                    for (Object v : values) {
                        if (internalType != null && ObjectField.NUMBER_TYPE.equals(internalType)) {
                            throw new IllegalArgumentException(operator + " number not allowed");
                        }
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (v != null && v instanceof Boolean) {
                            if (internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                                throw new IllegalArgumentException(operator + " region with boolean not allowed");
                            }
                        }
                        if (internalType != null && ObjectField.UUID_TYPE.equals(internalType)) {
                            if (v == null || (v != null && v.equals(new UUID(0, 0)))) {
                                throw new IllegalArgumentException(operator + " UUID of null/0 not allowed");
                            } else {
                                throw new IllegalArgumentException(operator + " UUID does not allow");
                            }
                        }
                        if (v instanceof Location || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                            if ((internalType == null) || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                                throw new IllegalArgumentException(operator + " location not allowed");
                            } else if (!ObjectField.REGION_TYPE.equals(internalType) && !ObjectField.LOCATION_TYPE.equals(internalType)) {
                                throw new IllegalArgumentException(operator + " location not allowed except for region/location");
                            }
                        }
                    }

                    String finalSimpleKey1 = simpleKey;
                    if (internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                        return combine(operator, values, BoolQueryBuilder::should, v ->
                                v == null ? QueryBuilders.matchAllQuery()
                                : "*".equals(v)
                                ? QueryBuilders.matchAllQuery()
                                : (v instanceof Location
                                    ? QueryBuilders.boolQuery().must(geoShapeIntersects(key + "." + REGION_FIELD, ((Location) v).getX(), ((Location) v).getY()))
                                    : (v instanceof Region
                                        ? QueryBuilders.boolQuery().must(
                                                equalsAnyQuery(finalSimpleKey1, key, key, query, v, ShapeRelation.CONTAINS))
                                        : QueryBuilders.queryStringQuery(String.valueOf(containsWildcard(operator, matchesAnyUUID(operator, key, v)))).field(matchesAnalyzer(operator, key, typeIds)))));
                                          //QueryBuilders.matchPhrasePrefixQuery(key, v))));
                    } else {
                        return combine(operator, values, BoolQueryBuilder::should, v ->
                                v == null ? QueryBuilders.matchAllQuery()
                                : "*".equals(v)
                                ? QueryBuilders.matchAllQuery()
                                : QueryBuilders.queryStringQuery(String.valueOf(containsWildcard(operator, matchesAnyUUID(operator, key, v)))).field(matchesAnalyzer(operator, key, typeIds)));
                    }

                case PredicateParser.MATCHES_ALL_OPERATOR :

                    if (!ANY_FIELD.equals(key)) {
                        mappedKey = mapFullyDenormalizedKey(query, key);
                        checkField = specialFields.get(mappedKey);
                        if (checkField == null) {
                            internalType = mappedKey.getInternalType();
                        }
                    }

                    for (Object v : values) {
                        if (v != null && Query.MISSING_VALUE.equals(v)) {
                            throw new IllegalArgumentException(operator + " missing not allowed");
                        }
                        if (v != null && v instanceof Boolean) {
                            if (internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                                throw new IllegalArgumentException(operator + " region with boolean not allowed");
                            }
                        }
                        if (internalType != null && ObjectField.UUID_TYPE.equals(internalType)) {
                            if (v == null || (v != null && v.equals(new UUID(0, 0)))) {
                                throw new IllegalArgumentException(operator + " UUID of null/0 not allowed");
                            } else {
                                throw new IllegalArgumentException(operator + " UUID does not allow");
                            }
                        }
                        if (v != null && v instanceof Location) {
                            if ((internalType == null) || (internalType != null && ObjectField.LOCATION_TYPE.equals(internalType))) {
                                throw new IllegalArgumentException(operator + " location not allowed");
                            } else if (!ObjectField.REGION_TYPE.equals(internalType) && !ObjectField.LOCATION_TYPE.equals(internalType)) {
                                throw new IllegalArgumentException(operator + " location not allowed except for region/location");
                            }
                        }
                    }

                    String finalSimpleKey2 = simpleKey;
                    if (internalType != null && ObjectField.REGION_TYPE.equals(internalType)) {
                        return combine(operator, values, BoolQueryBuilder::must, v ->
                                v == null ? QueryBuilders.matchAllQuery()
                                        : "*".equals(v)
                                        ? QueryBuilders.matchAllQuery()
                                        : (v instanceof Location
                                        ? QueryBuilders.boolQuery().must(geoShapeIntersects(key + "." + REGION_FIELD, ((Location) v).getX(), ((Location) v).getY()))
                                        : (v instanceof Region
                                        ? QueryBuilders.boolQuery().must(
                                        equalsAnyQuery(finalSimpleKey2, key, key, query, v, ShapeRelation.CONTAINS))
                                        : QueryBuilders.queryStringQuery(String.valueOf(matchesAnyUUID(operator, key, v))).field(matchesAnalyzer(operator, key, typeIds)))));
                                       //QueryBuilders.matchPhrasePrefixQuery(key, v))));
                    } else {
                        return combine(operator, values, BoolQueryBuilder::must, v ->
                                v == null ? QueryBuilders.matchAllQuery()
                                        : "*".equals(v)
                                        ? QueryBuilders.matchAllQuery()
                                        : QueryBuilders.queryStringQuery(String.valueOf(matchesAnyUUID(operator, key, v))).field(matchesAnalyzer(operator, key, typeIds)));
                    }

                case PredicateParser.MATCHES_EXACT_ANY_OPERATOR :
                case PredicateParser.MATCHES_EXACT_ALL_OPERATOR :
                default :
                    break;
            }
        }

        throw new UnsupportedPredicateException(this, predicate);
    }

    /**
     * Check String for UUID
     */
    private boolean isUUID(Object obj) {
        try {
            if (obj instanceof UUID) {
                return true;
            } else if (obj instanceof String) {
                //noinspection ResultOfMethodCallIgnored
                UUID.fromString((String) obj);
                return true;
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return false;
    }

    /**
     * Combines the items and operators
     *
     * @see #predicateToQueryBuilder(Predicate, Query)
     */
    @SuppressWarnings("unchecked")
    private <T> QueryBuilder combine(String operatorType,
                                     Collection<T> items,
                                     BiFunction<BoolQueryBuilder, QueryBuilder, BoolQueryBuilder> operator,
                                     Function<T, QueryBuilder> itemFunction) {

        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        if (items.size() == 0) {
            LOGGER.debug("builder: {} mustNot matchAllQuery", builder.toString());
            return QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery());
        }
        for (T item : items) {
            if (item == null) {
                operator = BoolQueryBuilder::mustNot;
            }
            if (item instanceof java.util.UUID) {
                item = (T) item.toString();
            }

            if (Query.MISSING_VALUE.equals(item)) {
                if (operatorType.equals(PredicateParser.EQUALS_ANY_OPERATOR)) {
                    operator = BoolQueryBuilder::mustNot;
                }
                if (operatorType.equals(PredicateParser.NOT_EQUALS_ALL_OPERATOR)) {
                    operator = BoolQueryBuilder::must;
                }
                builder = operator.apply(builder, itemFunction.apply(item));
            } else {
                builder = operator.apply(builder, itemFunction.apply(item));
            }
        }

        if (builder.hasClauses()) {
            return builder;
        } else {
            return QueryBuilders.matchAllQuery();
        }
    }

    /**
     * Force a flush if isImmediate since it needs to be committed now, otherwise just do a refresh which
     * is lightweight.
     */
    private static void refresh(TransportClient client, boolean isImmediate) {
        if (client != null) {
            if (isImmediate) {
                client.admin().indices().prepareFlush().get();
            }
            client.admin().indices().prepareRefresh().get();
        }
    }

    /**
     * Force a flush if isImmediate since it needs to be committed now, otherwise just do a refresh which
     * is lightweight.
     */
    @Override
    protected void commitTransaction(TransportClient client, boolean isImmediate) throws Exception {
        refresh(client, isImmediate);
    }

    /**
     * The settings for indexes analysis
     */
    public static String getSetting(String path) {
        try {
            LOGGER.debug("trying resource mapping.json mapping");
            return new String(Files.readAllBytes(Paths.get(ElasticsearchDatabase.class.getClassLoader().getResource(path + "setting.json").toURI())));
        } catch (Exception error) {
            LOGGER.info("Using default setting - switch to resource file");
            return "{\n"
                    + "  \"index.query.default_field\": \"_any\",\n"
                    + "  \"index.mapping.total_fields.limit\": 100000,\n"
                    + "  \"index.mapping.ignore_malformed\": true,\n"
                    + "  \"index\": {\n"
                    + "    \"refresh_interval\" : \"2s\",\n"
                    + "    \"number_of_shards\": \"2\",\n"
                    + "    \"number_of_replicas\": \"1\"\n"
                    + "  },\n"
                    + "  \"analysis\": {\n"
                    + "    \"analyzer\": {\n"
                    + "      \"text_analyzer\": {\n"
                    + "        \"char_filter\": [ \"html_strip\" ],\n"
                    + "        \"tokenizer\": \"whitespace\",\n"
                    + "        \"filter\" : [\"text_delimiter\", \"lowercase\", \"text_stemmer\"]\n"
                    + "      },\n"
                    + "      \"any_analyzer\": {\n"
                    + "          \"char_filter\": [ \"html_strip\" ],\n"
                    + "          \"tokenizer\": \"whitespace\",\n"
                    + "          \"filter\" : [\"text_delimiter\", \"lowercase\", \"text_stemmer\", \"unique_words\"]\n"
                    + "      },\n"
                    + "      \"suggest_analyzer\": {\n"
                    + "        \"char_filter\": [ \"html_strip\" ],\n"
                    + "        \"tokenizer\": \"whitespace\",\n"
                    + "        \"filter\" : [\"suggest_delimiter\", \"lowercase\", \"ngram_filter\"]\n"
                    + "      },\n"
                    + "      \"search_suggest_analyzer\": {\n"
                    + "        \"char_filter\": [ \"html_strip\" ],\n"
                    + "        \"tokenizer\": \"whitespace\",\n"
                    + "        \"filter\" : [\"suggest_delimiter\", \"lowercase\"]\n"
                    + "      }\n"
                    + "    },\n"
                    + "    \"filter\" : {\n"
                    + "      \"ngram_filter\": {\n"
                    + "        \"type\": \"edge_ngram\",\n"
                    + "        \"min_gram\": 1,\n"
                    + "        \"max_gram\": 12,\n"
                    + "        \"token_chars\": [\n"
                    + "          \"letter\",\n"
                    + "          \"digit\"\n"
                    + "        ]\n"
                    + "      },\n"
                    + "      \"suggest_delimiter\" : {\n"
                    + "        \"type\" : \"word_delimiter\",\n"
                    + "        \"catenate_all\": false,\n"
                    + "        \"catenate_numbers\": false,\n"
                    + "        \"catenate_words\": false,\n"
                    + "        \"generate_number_parts\": true,\n"
                    + "        \"generate_word_parts\": true,\n"
                    + "        \"split_on_case_change\": true\n"
                    + "      },\n"
                    + "      \"text_delimiter\" : {\n"
                    + "        \"type\" : \"word_delimiter\",\n"
                    + "        \"catenate_all\": false,\n"
                    + "        \"catenate_numbers\": true,\n"
                    + "        \"catenate_words\": true,\n"
                    + "        \"generate_number_parts\": true,\n"
                    + "        \"generate_word_parts\": true,\n"
                    + "        \"split_on_case_change\": true\n"
                    + "      },\n"
                    + "      \"text_stemmer\" : {\n"
                    + "        \"type\" : \"stemmer\",\n"
                    + "        \"name\" : \"porter2\"\n"
                    + "      },\n"
                    + "      \"unique_words\" : {\n"
                    + "        \"type\" : \"unique\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  }\n"
                    + "}";
        }
    }

    /**
     * The actual map for Elasticsearch
     */
    private static String getMapping(String path) {
        try {
            LOGGER.debug("trying resource mapping.json mapping");
            return new String(Files.readAllBytes(Paths.get(ElasticsearchDatabase.class.getClassLoader().getResource(path + "mapping.json").toURI())));
        } catch (Exception error) {
            LOGGER.info("Using default mapping - switch to resource file");
            return "{\n"
                    + "  \"_all\" : {\"enabled\" : false},\n"
                    + "  \"properties\" : {\n"
                    + "    \"_ids\": {\n"
                    + "      \"type\": \"keyword\"\n"
                    + "    },\n"
                    + "    \"_any\": {\n"
                    + "      \"type\": \"text\",\n"
                    + "      \"analyzer\": \"any_analyzer\"\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"dynamic_templates\": [\n"
                    + "    {\n"
                    + "      \"locationgeo\": {\n"
                    + "        \"match\": \"_location\",\n"
                    + "        \"match_mapping_type\": \"string\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"geo_point\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"boolean_type\": {\n"
                    + "        \"match\": \"_boolean\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"boolean\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"date_type\": {\n"
                    + "        \"match\": \"_date\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"long\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"number_type\": {\n"
                    + "        \"match\": \"_number\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"double\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"string_type\": {\n"
                    + "        \"match\": \"_string\",\n"
                    + "        \"match_mapping_type\": \"string\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"text\",\n"
                    + "          \"fields\": {\n"
                    + "            \"raw\": {\n"
                    + "              \"type\": \"keyword\",\n"
                    + "              \"ignore_above\": 512\n"
                    + "            },\n"
                    + "            \"match\": {\n"
                    + "              \"type\": \"text\",\n"
                    + "              \"analyzer\": \"text_analyzer\"\n"
                    + "            }\n"
                    + "          }\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"suggest_type\": {\n"
                    + "        \"match\": \"_suggest\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"text\",\n"
                    + "          \"analyzer\": \"suggest_analyzer\",\n"
                    + "          \"search_analyzer\": \"search_suggest_analyzer\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"shapegeo\": {\n"
                    + "        \"match\": \"_polygon\",\n"
                    + "        \"match_mapping_type\": \"object\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"geo_shape\"\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"data_template_top\": {\n"
                    + "        \"match\": \"data\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"{dynamic_type}\",\n"
                    + "          \"include_in_all\": false,\n"
                    + "          \"index\": false,\n"
                    + "          \"ignore_malformed\": true\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"data_template\": {\n"
                    + "        \"path_match\": \"data.*\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"{dynamic_type}\",\n"
                    + "          \"include_in_all\": false,\n"
                    + "          \"index\": false,\n"
                    + "          \"ignore_malformed\": true\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"int_template\": {\n"
                    + "        \"match\": \"_*\",\n"
                    + "        \"match_mapping_type\": \"string\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"keyword\",\n"
                    + "          \"ignore_above\": 1024\n"
                    + "        }\n"
                    + "      }\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"notanalyzed\": {\n"
                    + "        \"match\": \"*\",\n"
                    + "        \"match_mapping_type\": \"string\",\n"
                    + "        \"mapping\": {\n"
                    + "          \"type\": \"text\",\n"
                    + "          \"fields\": {\n"
                    + "            \"raw\": {\n"
                    + "              \"type\": \"keyword\",\n"
                    + "              \"ignore_above\": 512\n"
                    + "            },\n"
                    + "            \"match\": {\n"
                    + "              \"type\": \"text\",\n"
                    + "              \"analyzer\": \"text_analyzer\"\n"
                    + "            }\n"
                    + "          }\n"
                    + "        }\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n";
        }
    }

    /**
     * Elastic mapping which will set the types used for Elastic on index creation
     *
     */
    private static synchronized void defaultMap(TransportClient client, String indexName, int shardsMax) {

        if (client != null) {
            boolean indexExists = client.admin().indices()
                    .prepareExists(indexName)
                    .execute().actionGet().isExists();

            if (!indexExists) {
                CreateIndexRequestBuilder cirb = client.admin().indices().prepareCreate(indexName).addMapping("_default_", ELASTIC_MAPPING)
                        .setSettings(ELASTIC_SETTING);
                CreateIndexResponse createIndexResponse = cirb.execute().actionGet();
                if (!createIndexResponse.isAcknowledged()) {
                    LOGGER.warn("Could not create index {}", indexName);
                }

                int current = 1000;   // default is NULL
                Settings currentVal = client.admin().cluster().prepareState().execute().actionGet().getState().metaData().persistentSettings();
                if (currentVal.get("action.search.shard_count.limit") != null) {
                    current = Integer.valueOf(currentVal.get("action.search.shard_count.limit"));
                }

                if (current != shardsMax) {
                    client.admin().cluster().prepareUpdateSettings().setPersistentSettings(
                            Settings.builder().put("action.search.shard_count.limit", String.valueOf(shardsMax)).build()).execute().actionGet();
                }

                ClusterHealthResponse yellow = client.admin().cluster().prepareHealth(indexName)
                        .setWaitForYellowStatus()
                        .setTimeout(TimeValue.timeValueSeconds(10))
                        .get();

                try {
                    refresh(client, false);
                } catch (Exception e) {
                    LOGGER.warn("Refresh failed");
                }
            }
        }
    }

    /**
     * Delete the index, can be used to reset the mapping for the index
     *
     */
    public void deleteIndex(String indexName) {
        if (client != null) {
            IndicesExistsRequest existsRequest = client.admin().indices().prepareExists(indexName).request();
            if (client.admin().indices().exists(existsRequest).actionGet().isExists()) {
                LOGGER.info("index {} exists... deleting!", indexName);
                DeleteIndexResponse response = client.admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet();
                if (!response.isAcknowledged()) {
                    LOGGER.error("Failed to delete elastic search index named {}", indexName);
                }
            }
            client.close();
            client = null;
            this.client = openConnection();
        }
    }

    /**
     * getElasticGeometryMap Create new Map that can be consumed for geoShape
     */
    private static Map<String, Object> getElasticGeometryMap(Map<String, Object> valueMap, String name) {
        Map<String, Object> newValueMap = new HashMap<>();

        if (valueMap.size() > 2) {
            if (valueMap.containsKey("polygons") && valueMap.containsKey("circles") && valueMap.containsKey("radius")) {
                newValueMap.put("type", "geometrycollection");

                List<Map<String, Object>> newGeometries = new ArrayList<>();
                if (valueMap.get("polygons") != null && valueMap.get("polygons") instanceof List) {
                    List polygons = (List) valueMap.get("polygons");
                    if (polygons.size() > 0) {
                        Map<String, Object> newObject = new HashMap<>();
                        newObject.put("type", "multipolygon");
                        List<List<List<List<Double>>>> newPolygons = new ArrayList<>();
                        for (Object p : polygons) {
                            List<List<List<Double>>> newPolygon = new ArrayList<>();
                            if (p instanceof List) {
                                for (Object ring : (List) p) {
                                    List<List<Double>> newRing = new ArrayList<>();
                                    for (Object latlon : (List) ring) {
                                        List<Double> newLatLon = new ArrayList<>();

                                        Double lat = (Double) ((List) latlon).get(0);
                                        Double lon = (Double) ((List) latlon).get(1);
                                        newLatLon.add(lat);
                                        newLatLon.add(lon);
                                        newRing.add(newLatLon);
                                    }
                                    newPolygon.add(newRing);
                                }
                                newPolygons.add(newPolygon);
                            }
                        }
                        newObject.put("coordinates", newPolygons);
                        newGeometries.add(newObject);
                    }
                }

                if (valueMap.get("circles") != null && valueMap.get("circles") instanceof List) {
                    List circles = (List) valueMap.get("circles");
                    if (circles.size() > 0) {
                        for (Object c : circles) {
                            if (c instanceof List) {
                                Map<String, Object> newGeometry = new HashMap<>();
                                List<Double> newCircle = new ArrayList<>();

                                newGeometry.put("type", "circle");
                                Double lat = (Double) ((List) c).get(0);
                                Double lon = (Double) ((List) c).get(1);
                                Double r = (Double) ((List) c).get(2);

                                newCircle.add(lat);
                                newCircle.add(lon);
                                newGeometry.put("coordinates", newCircle);
                                newGeometry.put("radius", Math.ceil(Region.degreesToMeters(r)) + "m");
                                newGeometries.add(newGeometry);
                            }
                        }
                    }
                }
                newValueMap.put("geometries", newGeometries);
            }
        }
        return newValueMap;
    }

    /**
     * Take the polygons and circles used for Region, and convert to Elastic format
     * This removes valueMap items and converts to new geoJson
     *
     */
    private static void convertToGeometryCollection(Map<String, Object> valueMap, String name) {

        if (valueMap.size() > 2) {
            if (valueMap.containsKey("polygons") && valueMap.containsKey("circles") && valueMap.containsKey("radius")) {

                List<Map<String, Object>> newGeometries = new ArrayList<>();
                if (valueMap.get("polygons") != null && valueMap.get("polygons") instanceof List) {
                    List polygons = (List) valueMap.get("polygons");
                    if (polygons.size() > 0) {
                        Map<String, Object> newObject = new HashMap<>();
                        newObject.put("type", "multipolygon");
                        List<List<List<List<Double>>>> newPolygons = new ArrayList<>();
                        for (Object p : polygons) {
                            List<List<List<Double>>> newPolygon = new ArrayList<>();
                            if (p instanceof List) {
                                for (Object ring : (List) p) {
                                    List<List<Double>> newRing = new ArrayList<>();
                                    for (Object latlon : (List) ring) {
                                        List<Double> newLatLon = new ArrayList<>();

                                        Double lat = (Double) ((List) latlon).get(0);
                                        Double lon = (Double) ((List) latlon).get(1);
                                        newLatLon.add(lat);
                                        newLatLon.add(lon);
                                        newRing.add(newLatLon);
                                    }
                                    newPolygon.add(newRing);
                                }
                                newPolygons.add(newPolygon);
                            }
                        }
                        newObject.put("coordinates", newPolygons);
                        newGeometries.add(newObject);
                    }
                }

                if (valueMap.get("circles") != null && valueMap.get("circles") instanceof List) {
                    List circles = (List) valueMap.get("circles");
                    if (circles.size() > 0) {
                        for (Object c : circles) {
                            if (c instanceof List) {
                                Map<String, Object> newGeometry = new HashMap<>();
                                List<Double> newCircle = new ArrayList<>();

                                newGeometry.put("type", "circle");
                                Double lat = (Double) ((List) c).get(0);
                                Double lon = (Double) ((List) c).get(1);
                                Double r = (Double) ((List) c).get(2);

                                newCircle.add(lat);
                                newCircle.add(lon);
                                newGeometry.put("coordinates", newCircle);
                                newGeometry.put("radius", Math.ceil(Region.degreesToMeters(r)) + "m");
                                newGeometries.add(newGeometry);
                            }
                        }
                    }
                }
                if (valueMap.containsKey("x")) {
                    valueMap.remove("x");
                }
                if (valueMap.containsKey("y")) {
                    valueMap.remove("y");
                }
                if (valueMap.containsKey("radius")) {
                    valueMap.remove("radius");
                }
                if (valueMap.containsKey("circles")) {
                    valueMap.remove("circles");
                }
                if (valueMap.containsKey("polygons")) {
                    valueMap.remove("polygons");
                }
            }
        }
    }

    /**
     * Main function to convert a Geometry to Elastic before writing. This is destructive.
     *
     * @see #doWrites
     * @see #convertLocationToName(Map, String)
     *
     */
    @SuppressWarnings("unchecked")
    private static void convertRegionToName(Map<String, Object> map, String name) {

        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> pair = it.next();
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) value;

                convertToGeometryCollection(valueMap, name);
                convertRegionToName((Map<String, Object>) value, name);
            } else if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        Map<String, Object> valueMap = (Map<String, Object>) item;

                        convertToGeometryCollection(valueMap, name);
                        convertRegionToName((Map<String, Object>) item, name);
                    }
                }
            }
        }
    }

    /**
     * Main function to convert a Geometry to Elastic before writing. This is destructive.
     *
     * @see #doWrites
     * @see #convertRegionToName(Map, String)
     *
     */
    private static void convertLocationToName(Map<String, Object> map, String name) {

        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> pair = it.next();
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> valueMap = (Map<String, Object>) value;
                if (valueMap.size() == 2) {
                    if (valueMap.get("x") != null && valueMap.get("y") != null) {
                        valueMap.put(name, valueMap.get("x") + "," + valueMap.get("y"));
                    }
                }
                //noinspection unchecked
                convertLocationToName((Map<String, Object>) value, name);

            } else if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> valueMap = (Map<String, Object>) item;
                        if (valueMap.size() == 2) {
                            if (valueMap.get("x") != null && valueMap.get("y") != null) {
                                valueMap.put(name, valueMap.get("x") + "," + valueMap.get("y"));
                            }
                        }
                        //noinspection unchecked
                        convertLocationToName((Map<String, Object>) item, name);
                    }
                }
            }
        }
    }

    @Override
    public void addUpdateNotifier(UpdateNotifier<?> notifier) {
        updateNotifiers.add(notifier);
    }

    @Override
    public void removeUpdateNotifier(UpdateNotifier<?> notifier) {
        updateNotifiers.remove(notifier);
    }

    // --- TypeAheadFieldsProcessor ---
    @Documented
    @Inherited
    @ObjectType.AnnotationProcessorClass(TypeAheadFieldsProcessor.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TypeAheadFields {
        String[] value() default { };
        TypeAheadFieldsMapping[] mappings() default { };
    }

    public @interface TypeAheadFieldsMapping {
        String field();
        String[] fields();
    }

    private static class TypeAheadFieldsProcessor implements ObjectType.AnnotationProcessor<TypeAheadFields> {
        @Override
        public void process(ObjectType type, TypeAheadFields annotation) {
            Map<String, List<String>> typeAheadFieldsMap = new HashMap<String, List<String>>();

            for (TypeAheadFieldsMapping mapping : annotation.mappings()) {
                List<String> fields = Arrays.asList(mapping.fields());
                typeAheadFieldsMap.put(mapping.field(), fields);
            }

            Collections.addAll(type.as(TypeModification.class).getTypeAheadFields(), annotation.value());
            type.as(TypeModification.class).setTypeAheadFieldsMap(typeAheadFieldsMap);

        }
    }

    @TypeModification.FieldInternalNamePrefix("elastic.")
    public static class TypeModification extends Modification<ObjectType> {

        private Set<String> typeAheadFields;
        private Map<String, List<String>> typeAheadFieldsMap;

        public Set<String> getTypeAheadFields() {
            if (typeAheadFields == null) {
                typeAheadFields = new HashSet<>();
            }
            return typeAheadFields;
        }

        public void setTypeAheadFields(Set<String> typeAheadFields) {
            this.typeAheadFields = typeAheadFields;
        }

        public Map<String, List<String>> getTypeAheadFieldsMap() {
            if (null == typeAheadFieldsMap) {
                typeAheadFieldsMap = new HashMap<String, List<String>>();
            }
            return typeAheadFieldsMap;
        }

        public void setTypeAheadFieldsMap(Map<String, List<String>> typeAheadFieldsMap) {
            this.typeAheadFieldsMap = typeAheadFieldsMap;
        }
    }
    // --- TypeAheadFieldsProcessor ---

    // --- ExcludeFromAnyProcessor  ---
    @Documented
    @Inherited
    @ObjectField.AnnotationProcessorClass(ExcludeFromAnyProcessor.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ExcludeFromAny {
        boolean value() default true;
    }

    private static class ExcludeFromAnyProcessor implements ObjectField.AnnotationProcessor<ElasticsearchDatabase.ExcludeFromAny> {
        @Override
        public void process(ObjectType type, ObjectField field, ElasticsearchDatabase.ExcludeFromAny annotation) {
            field.as(ElasticsearchDatabase.FieldData.class).setExcludeFromAny(annotation.value());
        }
    }

    @Modification.FieldInternalNamePrefix("elastic.")
    public static class FieldData extends Modification<ObjectField> {

        private boolean excludeFromAny;

        public boolean isExcludeFromAny() {
            return excludeFromAny;
        }

        public void setExcludeFromAny(boolean excludeFromAny) {
            this.excludeFromAny = excludeFromAny;
        }
    }
    // --- ExcludeFromAnyProcessor  ---

    private static final char[] UUID_WORD_CHARS = new char[] {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
            'y', 'z' };

    /**
     * Split up UUID
     */
    private static String uuidToWord(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        byte[] bytes = UuidUtils.toBytes(uuid);
        int bytesLength = bytes.length;
        int wordLast = bytesLength * 2;
        byte currentByte;
        char[] word = new char[wordLast + 1];

        for (int byteIndex = 0, hexIndex = 0;
             byteIndex < bytesLength;
             ++ byteIndex, ++ hexIndex) {

            currentByte = bytes[byteIndex];
            word[hexIndex] = UUID_WORD_CHARS[(currentByte & 0xf0) >> 4];
            ++ hexIndex;
            word[hexIndex] = UUID_WORD_CHARS[(currentByte & 0x0f)];
        }

        word[wordLast] = 'z';
        return new String(word);
    }

    /**
     * Add Indexed Values and Indexed Methods to Elastic
     */
    public void addDocumentValues(Map<String, Object> extras, StringBuilder allBuilder, boolean includeInAny, ObjectField field, String name, Object value) {

        if (value == null) {
            return;
        }

        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                addDocumentValues(extras, allBuilder, includeInAny, field, name, item);
            }
            return;
        }

        if (includeInAny) {
            includeInAny = field == null
                    || !field.as(FieldData.class).isExcludeFromAny();
        }

        if (value instanceof Recordable) {
            value = ((Recordable) value).getState().getSimpleValues();
        }

        if (value instanceof Map) {
            Map<?, ?> valueMap = (Map<?, ?>) value;

            if (field.getInternalItemType().equals(ObjectField.LOCATION_TYPE)) {
                if (valueMap.containsKey("x") && valueMap.containsKey("y")) {
                    addDocumentValues(extras, allBuilder, includeInAny, field, name + ".x", valueMap.get("x"));
                    addDocumentValues(extras, allBuilder, includeInAny, field, name + ".y", valueMap.get("y"));
                    name = name + "." + LOCATION_FIELD;
                    value = valueMap.get("x") + "," + valueMap.get("y");
                } else {
                    return;
                }

            } else if (field.getInternalItemType().equals(ObjectField.REGION_TYPE)) {

                @SuppressWarnings("unchecked") Map<String, Object> m = getElasticGeometryMap((Map<String, Object>) valueMap, name);
                if (m.size() > 0) {
                    name = name + "." + REGION_FIELD;
                    value = m;
                } else {
                    return;
                }

            } else {
                UUID valueTypeId = ObjectUtils.to(UUID.class, valueMap.get(StateSerializer.TYPE_KEY));

                if (valueTypeId == null) {
                    if (field != null && ObjectField.RECORD_TYPE.equals(field.getInternalItemType())) {
                        for (Object item : valueMap.values()) {
                            if (item instanceof Map) {
                                addDocumentValues(extras, allBuilder, includeInAny, field, name, item);
                            }
                        }

                    } else {
                        // We could keep track of the level - and MAP 1 more level with entrySet() name + "/" + getKey()
                        for (Object item : valueMap.values()) {
                            addDocumentValues(extras, allBuilder, includeInAny, field, name, item);
                        }
                    }
                    return;

                } else {
                    UUID valueId = ObjectUtils.to(UUID.class, valueMap.get(StateSerializer.REFERENCE_KEY));

                    if (valueId == null) {
                        if (includeInAny) {
                            allBuilder.append(valueTypeId).append(' ');
                            allBuilder.append(uuidToWord(valueTypeId)).append(' ');
                        }

                        ObjectType valueType = getEnvironment().getTypeById(valueTypeId);

                        if (valueType != null) {
                            State valueState = null;
                            if (!valueType.getMethods().isEmpty()) {
                                valueState = new State();
                                valueState.setType(valueType);
                                valueState.setId(ObjectUtils.to(UUID.class, valueMap.get(StateSerializer.ID_KEY)));
                            }
                            for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                                String subName = entry.getKey().toString();
                                ObjectField subField = valueType.getField(subName);

                                if (subField != null) {
                                    addDocumentValues(
                                            extras,
                                            allBuilder,
                                            includeInAny,
                                            subField,
                                            name + "/" + subName,
                                            entry.getValue());
                                }
                                if (valueState != null) {
                                    valueState.putByPath(subName, entry.getValue());
                                }
                            }
                            if (valueState != null) {
                                for (ObjectMethod method : valueType.getMethods()) {
                                    addDocumentValues(
                                            extras,
                                            allBuilder,
                                            includeInAny,
                                            method,
                                            name + "/" + method.getInternalName(),
                                            Static.getStateMethodValue(valueState, method)
                                    );
                                }
                            }
                        }
                        return;

                    } else {
                        value = valueId;
                        Set<ObjectField> denormFields = field.getEffectiveDenormalizedFields(getEnvironment().getTypeById(valueTypeId));

                        if (denormFields != null) {
                            State valueState = State.getInstance(Query.from(Object.class).where("_id = ?", valueId).first());
                            if (valueState != null) {
                                Map<String, Object> valueValues = valueState.getSimpleValues();
                                for (ObjectField denormField : denormFields) {
                                    String denormFieldName = denormField.getInternalName();
                                    addDocumentValues(
                                            extras,
                                            allBuilder,
                                            includeInAny,
                                            denormField,
                                            name + "/" + denormFieldName,
                                            valueValues.get(denormFieldName));
                                }
                            }
                        }
                    }
                }
            }
        }

        String trimmed = value.toString().trim();
        if (trimmed.contains("<") || trimmed.contains(">")) {
            trimmed = Jsoup.parse(trimmed).text();
        }
        if (includeInAny) {
            Matcher uuidMatcher = UUID_PATTERN.matcher(trimmed);
            int uuidLast = 0;

            while (uuidMatcher.find()) {
                allBuilder.append(trimmed.substring(uuidLast, uuidMatcher.start())).append(' ');

                uuidLast = uuidMatcher.end();
                String word = uuidToWord(ObjectUtils.to(UUID.class, uuidMatcher.group(0)));

                if (word != null) {
                    allBuilder.append(word).append(' ');
                }
            }

            allBuilder.append(trimmed.substring(uuidLast)).append(' ');
        }

        if (value instanceof String) {
            value = ((String) value).trim();
            // don't save empty strings to be compatible with Solr exists
            if (((String) value).length() == 0) {
                return;
            }
        }

        Object truncatedValue = value;

        String fname = addIndexFieldType(field.getInternalItemType(), name, truncatedValue);
        if (fname.endsWith("." + STRING_FIELD) && !(truncatedValue instanceof String)) {
            truncatedValue = String.valueOf(truncatedValue);
        }
        //LOGGER.info("fname: {}, truncatedValue: {}", fname, truncatedValue.toString());

        // all field fair game is String and does not start with "_" which are IDs
        if (!name.startsWith("_")) {
            if (value instanceof String) {
                truncatedValue = trimmed;
                if (((String) truncatedValue).length() > MAX_BINARY_FIELD_LENGTH) {
                    truncatedValue = ((String) truncatedValue).substring(0, MAX_BINARY_FIELD_LENGTH);
                }
            }
        }

        if (extras.get(fname) == null) {
            setValue(extras, fname, truncatedValue);
        } else {
            addValue(extras, fname, truncatedValue);
        }
    }

    /** {@link ElasticsearchDatabase} utility methods. */
    public static final class Static {

        // Same in SOLR and Elastic since they are to be escaped in Lucene
        private static final Pattern ESCAPE_PATTERN = Pattern.compile("([-+&|!(){}\\[\\]^\"~*?:\\\\\\s/])");

        /**
         * Escapes the given {@code value} so that it's safe to use
         * in a Elastic query.
         *
         * @param value If {@code null}, returns {@code null}.
         */
        public static String escapeValue(Object value) {
            return value != null ? ESCAPE_PATTERN.matcher(value.toString()).replaceAll("\\\\$1") : null;
        }

        /**
         * Returns the Solr search result score associated with the given
         * {@code object}.
         *
         * @return May be {@code null} if the score isn't available.
         */
        public static Float getScore(Object object) {
            return (Float) State.getInstance(object).getExtra(SCORE_EXTRA);
        }

        /**
         * Returns the normalized Solr search result score, in a scale of
         * {@code 0.0} to {@code 1.0}, associated with the given
         * {@code object}.
         *
         * @return May be {@code null} if the score isn't available.
         */
        public static Float getNormalizedScore(Object object) {
            return (Float) State.getInstance(object).getExtra(NORMALIZED_SCORE_EXTRA);
        }

        /**
         * Execute an ObjectMethod on the given State and return the result as a value or reference.
         */
        private static Object getStateMethodValue(State state, ObjectMethod method) {
            Object methodResult = state.getByPath(method.getInternalName());
            return State.toSimpleValue(methodResult, method.isEmbedded(), false);
        }
    }

    /**
     * Set the value into extras
     */
    private static void setValue(Map<String, Object> extras, String name, Object value) {
        extras.put(name, value);
    }

    /**
     * Add value into extras
     */
    private static void addValue(Map<String, Object> extras, String name, Object value) {
        if (extras.get(name) instanceof List) {
            if (value instanceof List) {
                List vList = (List) extras.get(name);
                //noinspection unchecked
                vList.addAll((List) value);
                extras.put(name, vList);
            } else {
                List vList = (List) extras.get(name);
                if (!vList.contains(value)) {
                    //noinspection unchecked
                    vList.add(value);
                    extras.put(name, vList);
                }
            }
        } else {
            List vList = new ArrayList<>();
            //noinspection unchecked
            vList.add(extras.get(name));
            if (!vList.contains(value)) {
                //noinspection unchecked
                vList.add(value);
                extras.put(name, vList);
            }
        }
    }

    /**
     * addIndexedFields to Elastic
     */
    public Map<String, Object> addIndexedFields(State state, StringBuilder allBuilder) {
        Map<String, Object> m = new HashMap<>();

        if (state.getType() != null) {
            Map<String, Object> stateValues = state.getSimpleValues();

            for (Map.Entry<String, Object> entry : stateValues.entrySet()) {
                String fieldName = entry.getKey();

                ObjectField field = state.getField(fieldName);
                if (field == null) {
                    continue;
                }
                // Elastic is not fast at Reindexing, so index it by default, otherwise check to see if field id Indexed

                String uniqueName = field.getUniqueName();
                addDocumentValues(
                        m,
                        allBuilder,
                        true,
                        field,
                        uniqueName,
                        entry.getValue());
            }
        }
        return m;
    }

    /**
     * Thrown when a field does not exist when indexing a type ahead field
     */
    @SuppressWarnings("serial")
    public static class NoTypeAheadFieldException extends RuntimeException {

        private final String group;
        private final String key;

        public NoTypeAheadFieldException(String group, String key) {
            super(String.format("Can't index [%s] type ahead due to missing dependent field [%s]!", group, key));
            this.group = group;
            this.key = key;
        }

        public String getGroup() {
            return group;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * Generate the typeAhead fields to the Index
     */
    public Map<String, Object> addTypeAhead(State state) {
        Map<String, Object> m = new HashMap<>();

        ObjectType type = state.getType();

        if (type != null) {
            Set<String> typeAheadFields = type.as(TypeModification.class).getTypeAheadFields();
            Map<String, List<String>> typeAheadFieldsMap = type.as(TypeModification.class).getTypeAheadFieldsMap();

            if (!typeAheadFields.isEmpty()) {
                List<String> buffer = new ArrayList<>();
                for (String typeAheadField : typeAheadFields) {
                    String value = ObjectUtils.to(String.class, state.getByPath(typeAheadField));
                    // Hack for a client.
                    if (!ObjectUtils.isBlank(value)) {
                        value = value.replaceAll("\\{", "").replaceAll("\\}", "");
                        buffer.add(value);
                    }
                }
                if (buffer.size() > 0) {
                    ObjectField field = state.getField(DEFAULT_SUGGEST_FIELD);
                    if (field != null) {
                        String uniqueName = field.getUniqueName() + "." + SUGGEST_FIELD;
                        m.put(uniqueName, buffer);
                    } else {
                        throw new NoTypeAheadFieldException(type.getDisplayName(), DEFAULT_SUGGEST_FIELD);
                    }
                }
            }

            if (!typeAheadFieldsMap.isEmpty()) {
                for (Map.Entry<String, List<String>> entry : typeAheadFieldsMap.entrySet()) {
                    String typeAheadField = entry.getKey();
                    List<String> targetFields = entry.getValue();
                    String value = ObjectUtils.to(String.class, state.getByPath(typeAheadField));

                    if (!ObjectUtils.isBlank(targetFields) && !ObjectUtils.isBlank(value)) {
                        for (String targetField : targetFields) {
                            value = value.replaceAll("\\{", "").replaceAll("\\}", "");
                            ObjectField field = state.getField(targetField);
                            if (field != null) {
                                String uniqueName = field.getUniqueName() + "." + SUGGEST_FIELD;
                                List<String> n;
                                if (m.get(uniqueName) != null && m.get(uniqueName) instanceof List) {
                                    n = (List) m.get(uniqueName);
                                    n.add(value);
                                } else {
                                    n = new ArrayList<>();
                                    n.add(value);
                                }
                                m.put(uniqueName, n);
                            } else {
                                throw new NoTypeAheadFieldException(type.getDisplayName(), targetField);
                            }
                        }
                    }
                }
            }
        }

        return m;
    }

    /**
     *
     * The _label used for the record
     */
    public Map<String, Object> addLabel(State state) {
        Map<String, Object> m = new HashMap<>();

        m.put(Query.LABEL_KEY, state.getLabel().trim().toLowerCase(Locale.ENGLISH));
        return m;
    }

    /**
     * Used to add Indexed Methods for Elastic
     */
    public Map<String, Object> addIndexedMethods(State state, StringBuilder allBuilder) {
        Map<String, Object> m = new HashMap<>();

        if (state.getType() != null) {
            List<ObjectMethod> methods = new ArrayList<>(state.getType().getMethods());
            // when doing bulk with getEnvironment().getMethods() and hasGroup false get raise condition
            // state.getId().equals(GLOBALS_ID)
            //  methods.addAll(getEnvironment().getMethods());

            for (ObjectMethod method : methods) {
                addDocumentValues(
                        m,
                        allBuilder,
                        true,
                        method,
                        method.getUniqueName(),
                        Static.getStateMethodValue(state, method)
                );
            }
        }
        return m;
    }

    // Pass through distinct set of States to doSaves.
    @Override
    protected void doWriteRecalculations(TransportClient client, boolean isImmediate, Map<ObjectIndex, List<State>> recalculations) throws Exception {
        if (recalculations != null) {
            int count = 0;
            Set<State> states = new HashSet<>();
            for (Map.Entry<ObjectIndex, List<State>> entry : recalculations.entrySet()) {
                count++;
                states.addAll(entry.getValue());
            }
            if (count > 0) {
                doWrites(client, isImmediate, new ArrayList<>(states), new ArrayList<>(), new ArrayList<>());
            }
        }
    }

    // Pass through to doWriteRecalculations.
    @Override
    protected void doRecalculations(TransportClient client, boolean isImmediate, ObjectIndex index, List<State> states) throws Exception {
        Map<ObjectIndex, List<State>> recalculations = new HashMap<>();
        recalculations.put(index, states);
        doWriteRecalculations(client, isImmediate, recalculations);
    }

    /**
     * Write saves, indexes, deletes as a bulk Elastic operation
     *
     * @param indexes Not used
     */
    @Override
    protected void doWrites(TransportClient client, boolean isImmediate, List<State> saves, List<State> indexes, List<State> deletes) throws Exception {
        try {
            client = openConnection();
            if (client == null || !isAlive(client)) {
                LOGGER.warn("Connection not open for doWrites");
                return;
            }

            // a reindex is a save in Elastic
            if (indexes != null && !indexes.isEmpty()) {
                if (saves == null) {
                    saves = indexes;
                } else {
                    saves.addAll(indexes);
                }
            }

            /* verify all indexes exist */
            Set<String> indexSet = new HashSet<>();

            if (saves != null) {
                for (State state : saves) {
                    UUID documentType = state.getVisibilityAwareTypeId();
                    String newIndexname = indexName + getElasticIndexName(documentType);
                    indexSet.add(newIndexname);
                }
            }

            // double check for deletes
            if (deletes != null) {
                for (State state : deletes) {
                    String newIndexname = indexName + getElasticIndexName(state.getTypeId());
                    indexSet.add(newIndexname);
                }
            }

            checkIndexes(indexSet.toArray(new String[indexSet.size()]));

            BulkRequestBuilder bulk = client.prepareBulk();

            String indexName = getIndexName();

            if (saves != null) {
                // "groups" are set in context.xml are aggregate and limit the saves
                Set<String> databaseGroups = getGroups();
                Iterator<State> iterator = saves.iterator();
                while (iterator.hasNext()) {
                    State state = iterator.next();

                    try {
                        if (hasGroup) {
                            ObjectType type = state.getType();

                            if (type != null) {
                                boolean savable = false;

                                for (String typeGroup : type.getGroups()) {
                                    if (databaseGroups.contains(typeGroup)) {
                                        savable = true;
                                        break;
                                    }
                                }

                                if (!savable) {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        }

                        boolean isNew = state.isNew();
                        UUID documentUUID = state.getId();
                        String documentId = documentUUID.toString();

                        UUID documentTypeUUID = state.getVisibilityAwareTypeId();
                        String documentType = documentTypeUUID.toString();

                        String newIndexname = indexName + getElasticIndexName(documentTypeUUID);
                        List<AtomicOperation> atomicOperations = state.getAtomicOperations();
                        StringBuilder allBuilder = new StringBuilder();

                        if (isNew || atomicOperations.isEmpty()) {
                            Map<String, Object> t = new HashMap<>();
                            if (isJsonState(state)) {
                                t.put(DATA_FIELD, ObjectUtils.toJson(state.getSimpleValues()));
                            } else {
                                t.put(DATA_FIELD, state.getSimpleValues());
                            }

                            Map<String, Object> extraFields = addIndexedFields(state, allBuilder);
                            Map<String, Object> extraLabel = addLabel(state);
                            Map<String, Object> extraTypeAhead = addTypeAhead(state);
                            Map<String, Object> extraIndex = addIndexedMethods(state, allBuilder);

                            if (extraFields.size() > 0) {
                                extraFields.forEach((s, obj) -> t.put(s, obj));
                            }
                            if (extraLabel.size() > 0) {
                                extraLabel.forEach((s, obj) -> t.put(s, obj));
                            }
                            if (extraTypeAhead.size() > 0) {
                                extraTypeAhead.forEach((s, obj) -> t.put(s, obj));
                            }
                            if (extraIndex.size() > 0) {
                                extraIndex.forEach((s, obj) -> t.put(s, obj));
                            }
                            t.remove("_id");
                            t.remove("_type");
                            t.put(UPDATEDATE_FIELD, this.now());
                            t.put(IDS_FIELD, documentId); // Elastic range for iterator default _id will not work
                            t.put(ANY_FIELD, allBuilder.toString().trim());

                            LOGGER.debug("Elasticsearch doWrites saving index [{}] and _type [{}] and _id [{}] = [{}]",
                                    newIndexname, documentType, documentId, t.toString());
                            bulk.add(client.prepareIndex(newIndexname, documentType, documentId).setSource(t));

                        } else {
                            // there is no getting around it, must grab query to get old value and set it
                            boolean sendFullUpdate = false;
                            Object oldObject = Query
                                    .from(Object.class)
                                    .where("_id = ?", documentId)
                                    .using(this)
                                    .master()
                                    .noCache()
                                    .first();

                            if (oldObject == null) {
                                retryWrites();
                                break;
                            }

                            // Restore the data from the old object.
                            State oldState = State.getInstance(oldObject);
                            UUID oldTypeId = oldState.getVisibilityAwareTypeId();
                            UUID oldId = oldState.getId();

                            state.setValues(oldState.getValues());

                            if (!this.painlessModule) {
                                LOGGER.info("Painless module is not installed");
                                sendFullUpdate = true;
                            }

                            if (isJsonState(state)) {
                                sendFullUpdate = true;
                            }

                            // Reset to old First
                            for (AtomicOperation operation : atomicOperations) {
                                String field = operation.getField();
                                state.putByPath(field, oldState.getByPath(field));
                                if (field.indexOf('/') != -1) {
                                    sendFullUpdate = true;
                                }
                            }

                            for (AtomicOperation operation : atomicOperations) {
                                operation.execute(state); // sets state
                                if (!sendFullUpdate) {
                                    String field = operation.getField().trim();
                                    if (operation instanceof AtomicOperation.Increment) {
                                        double newVal = ((AtomicOperation.Increment) operation).getValue();
                                        Map<String, Object> params = new HashMap<>();
                                        params.put("val", newVal);
                                        bulk.add(client.prepareUpdate(newIndexname, documentType, documentId)
                                                .setScript(new Script(ScriptType.INLINE, "painless", "ctx._source." + DATA_FIELD + "." + field + " += params.val;", params))
                                                .setDetectNoop(false));
                                    } else if (operation instanceof AtomicOperation.Add) {
                                        Object newVal = ((AtomicOperation.Add) operation).getValue();
                                        Map<String, Object> params = new HashMap<>();
                                        params.put("val", newVal);
                                        bulk.add(client.prepareUpdate(newIndexname, documentType, documentId)
                                                .setScript(new Script(ScriptType.INLINE, "painless", "ctx._source." + DATA_FIELD + "." + field + ".add(params.val);", params))
                                                .setDetectNoop(false));
                                    } else if (operation instanceof AtomicOperation.Remove) {
                                        Object newVal = ((AtomicOperation.Remove) operation).getValue();
                                        Map<String, Object> params = new HashMap<>();
                                        params.put("val", newVal);
                                        bulk.add(client.prepareUpdate(newIndexname, documentType, documentId)
                                                .setScript(new Script(ScriptType.INLINE, "painless", "ctx._source." + DATA_FIELD + "." + field + ".remove(ctx._source." + DATA_FIELD + "." + field + ".indexOf(params.val));", params))
                                                .setDetectNoop(false));
                                    } else if (operation instanceof AtomicOperation.Put) {
                                        Object newVal = ((AtomicOperation.Put) operation).getValue();
                                        Map<String, Object> params = new HashMap<>();
                                        params.put("val", newVal);
                                        bulk.add(client.prepareUpdate(newIndexname, documentType, documentId)
                                                .setScript(new Script(ScriptType.INLINE, "painless", "ctx._source." + DATA_FIELD + "." + field + "= params.val;", params))
                                                .setDetectNoop(false));
                                    } else if (operation instanceof AtomicOperation.Replace) {
                                        Object oldVal = ((AtomicOperation.Replace) operation).getOldValue();
                                        Object newVal = ((AtomicOperation.Replace) operation).getNewValue();
                                        Map<String, Object> params = new HashMap<>();
                                        params.put("oval", oldVal);
                                        params.put("nval", newVal);
                                        bulk.add(client.prepareUpdate(newIndexname, documentType, documentId)
                                                .setScript(new Script(ScriptType.INLINE, "painless", "if (ctx._source." + DATA_FIELD + "." + field + " == params.oval) ctx._source." + DATA_FIELD + "." + field + "= params.nval;", params))
                                                .setDetectNoop(false));
                                    } else {
                                        sendFullUpdate = true;
                                    }
                                }
                                // other ones can be done with normal update() since merge should work.
                            }

                            boolean sendExtraUpdate = false;
                            Map<String, Object> t = new HashMap<>();
                            if (isJsonState(state)) {
                                t.put(DATA_FIELD, ObjectUtils.toJson(state.getSimpleValues()));
                            } else {
                                t.put(DATA_FIELD, state.getSimpleValues());
                            }
                            Map<String, Object> extra = new HashMap<>();
                            Map<String, Object> extraFields = addIndexedFields(state, allBuilder);
                            Map<String, Object> extraLabel = addLabel(state);
                            Map<String, Object> extraTypeAhead = addTypeAhead(state);
                            Map<String, Object> extraIndex = addIndexedMethods(state, allBuilder);

                            if (extraFields.size() > 0) {
                                sendExtraUpdate = true;
                                extraFields.forEach((s, obj) -> t.put(s, obj));
                                extraFields.forEach((s, obj) -> extra.put(s, obj));
                            }
                            if (extraLabel.size() > 0) {
                                sendExtraUpdate = true;
                                extraLabel.forEach((s, obj) -> t.put(s, obj));
                                extraLabel.forEach((s, obj) -> extra.put(s, obj));
                            }
                            if (extraTypeAhead.size() > 0) {
                                sendExtraUpdate = true;
                                extraTypeAhead.forEach((s, obj) -> t.put(s, obj));
                                extraTypeAhead.forEach((s, obj) -> extra.put(s, obj));
                            }
                            if (extraIndex.size() > 0) {
                                sendExtraUpdate = true;
                                extraIndex.forEach((s, obj) -> t.put(s, obj));
                                extraIndex.forEach((s, obj) -> extra.put(s, obj));
                            }
                            t.remove("_id");
                            t.remove("_type");
                            t.put(UPDATEDATE_FIELD,  this.now());
                            t.put(IDS_FIELD, documentId);
                            t.put(ANY_FIELD, allBuilder.toString().trim());

                            // if you move TypeId you need to add the whole document and remove old
                            if (!oldTypeId.equals(documentTypeUUID) || !oldId.equals(documentUUID)) {
                                String oldDocumentType = oldTypeId.toString();
                                String oldDocumentId = oldId.toString();
                                String oldIndexname = indexName + oldDocumentType.replaceAll("-", "");
                                bulk.add(client.prepareDelete(oldIndexname, oldDocumentType, oldDocumentId));
                                LOGGER.debug("Elasticsearch doWrites moved typeId/Id atomic add index [{}] and _type [{}] and _id [{}] = [{}]",
                                        new Object[] {newIndexname, documentType, documentId, t.toString()});
                                bulk.add(client.prepareIndex(newIndexname, documentType, documentId).setSource(t));
                            } else if (sendFullUpdate) {
                                LOGGER.debug("Elasticsearch doWrites sendFullUpdate atomic updating index [{}] and _type [{}] and _id [{}] = [{}]",
                                        new Object[] {newIndexname, documentType, documentId, t.toString()});
                                bulk.add(client.prepareUpdate(newIndexname, documentType, documentId).setDoc(t));
                            } else if (sendExtraUpdate) {
                                LOGGER.debug("Elasticsearch doWrites sendExtraUpdate atomic updating index [{}] and _type [{}] and _id [{}] = [{}]",
                                        new Object[] {newIndexname, documentType, documentId, extra.toString()});
                                bulk.add(client.prepareUpdate(newIndexname, documentType, documentId).setDoc(extra));
                            }
                        }
                    } catch (Exception error) {
                        LOGGER.warn(
                                String.format("Elasticsearch doWrites saves Exception [%s: %s]",
                                        error.getClass().getName(),
                                        error.getMessage()),
                                error);
                        throw error;
                    }
                }
            }

            if (deletes != null) {
                for (State state : deletes) {
                    String documentType = state.getTypeId().toString();
                    String documentId = state.getId().toString();
                    String newIndexname = indexName + getElasticIndexName(state.getTypeId());

                    LOGGER.debug("Elasticsearch doWrites deleting index [{}] and _type [{}] and _id [{}]",
                            new Object[]{newIndexname, documentType, documentId});
                    try {
                        bulk.add(client
                                .prepareDelete(newIndexname, state.getTypeId().toString(), state.getId().toString()));
                    } catch (Exception error) {
                        LOGGER.warn(
                                String.format("Elasticsearch doWrites deletes Exception [%s: %s]",
                                        error.getClass().getName(),
                                        error.getMessage()),
                                error);
                        throw error;
                    }
                }
            }

            if (bulk.numberOfActions() > 0) {
                LOGGER.debug("Elasticsearch Writing [{}]", bulk.numberOfActions());
                // this is 8 retries which should solve most issues
                Retry retry = Retry.on(EsRejectedExecutionException.class).policy(BackoffPolicy.exponentialBackoff());
                BulkResponse bulkResponse = retry.withSyncBackoff(client, bulk.request());
                if (bulkResponse.hasFailures()) {
                    BulkItemResponse[] resItems = bulkResponse.getItems();
                    for (int i = 0; i < resItems.length; i++) {
                        BulkItemResponse r = resItems[i];
                        if (r.isFailed()) {
                            ActionRequest ireq = bulk.request().requests().get(i);
                            LOGGER.warn("Errors on Bulk {} {} {} {} [{}]",
                                    new Object[] {r.getIndex(), r.getType(), r.getId(), r.getFailureMessage(), ireq});
                        }
                    }
                }
                if (isImmediate) {
                    commitTransaction(client, true);
                }
            } else {
                LOGGER.info("Elasticsearch bulk request had 0 actions");
            }
        } catch (Exception error) {
            LOGGER.warn(
                    String.format("Elasticsearch doWrites Exception [%s: %s]",
                            error.getClass().getName(),
                            error.getMessage()),
                    error);
            throw error;
        }
    }
}
