package com.psddev.dari.elasticsearch;

import com.psddev.dari.util.CollectionUtils;
import com.psddev.dari.util.SettingsException;
import org.elasticsearch.node.Node;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InitializationTest extends AbstractElasticTest {

    private ElasticsearchDatabase database;
    private Map<String, Object> settings;

    @Before
    public void before() {
        database = new ElasticsearchDatabase();
        settings = new HashMap<>();
    }

    private void put(String path, Object value) {
        CollectionUtils.putByPath(settings, path, value);
    }

    @Test
    public void embeddedElastic() {
        String nodeHost = "http://localhost:9200/";
        assertThat(ElasticsearchDatabase.checkAlive(nodeHost), is(true));

        String elasticCluster = ElasticsearchDatabase.getClusterName(nodeHost);
        assertThat(elasticCluster, is(notNullValue()));
        if (getIsEmbedded()) {
            Node node = EmbeddedElasticsearchServer.getNode();
            assertThat(node, is(notNullValue()));
        }

        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING, "index1");
        put(ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, elasticCluster);
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING+ "class", ElasticsearchDatabase.class.getName());
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING + "1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "9300");
        put(ElasticsearchDatabase.INDEX_NAME_SUB_SETTING + "1/" + ElasticsearchDatabase.CLUSTER_PORT_SUB_SETTING, "localhost");

        database.initialize("", settings);
    }

    @Test(expected = NullPointerException.class)
    public void testMissingSettings() {
        put(ElasticsearchDatabase.CLUSTER_NAME_SUB_SETTING, "foo");
        database.initialize("", settings);
    }
}
