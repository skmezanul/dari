package com.psddev.dari.elasticsearch;

import org.elasticsearch.client.transport.TransportClient;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class ElasticDatabaseConnectionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticDatabaseConnectionTest.class);

    @Test
    public void testMultipleConnections() {
        String nodeHost = "http://localhost:9200/";
        String elasticCluster = ElasticsearchDatabase.getClusterName(nodeHost);

        org.elasticsearch.common.settings.Settings nodeSettings;
        List<ElasticsearchNode> nodes = new ArrayList<>();

        nodeSettings = org.elasticsearch.common.settings.Settings.builder()
                .put("cluster.name", elasticCluster)
                .put("client.transport.sniff", true).build();

        ElasticsearchNode n = new ElasticsearchNode();
        n.port = 9300;
        n.restPort = 9200;
        n.hostname = "localhost";
        nodes.add(n);
        TransportClient c = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        TransportClient c1 = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        TransportClient c2 = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        assertThat(c.hashCode(), is(c1.hashCode()));
        assertThat(c1.hashCode(), is(c2.hashCode()));

        nodeSettings = org.elasticsearch.common.settings.Settings.builder()
                .put("cluster.name", "newcluster")
                .put("client.transport.sniff", true).build();

        TransportClient newConn = ElasticsearchDatabaseConnection.getClient(nodeSettings, nodes);
        assertThat(c.hashCode(), is(not(newConn.hashCode())));
        ElasticsearchDatabaseConnection.closeClients();
    }
}
