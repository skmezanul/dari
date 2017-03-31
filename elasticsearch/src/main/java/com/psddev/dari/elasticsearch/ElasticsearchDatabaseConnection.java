package com.psddev.dari.elasticsearch;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ElasticsearchDatabaseConnection {
    private static Map<String, TransportClient> clientConnections = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchDatabase.class);

    /**
     * Check nodes are not empty and isAlive
     *
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAlive(TransportClient client) {
        List<DiscoveryNode> nodes = client.connectedNodes();
        return !nodes.isEmpty();
    }

    /**
     * Generate hash that is only on nodes and cluster
     */
    private static String getHash(Settings nodeSettings, List<ElasticsearchNode> nodes) {
        StringBuilder hash = new StringBuilder();
        for (ElasticsearchNode n : nodes) {
            hash.append(n.hostname + " " + n.port + " " + n.restPort + " ");
        }
        hash.append(nodeSettings.get("cluster.name"));
        return hash.toString();
    }

    /**
     * Force a close
     */
    public static synchronized void closeClients() {
        Iterator<String> it = clientConnections.keySet().iterator();

        while (it.hasNext()){
            String key = it.next();
            TransportClient c = clientConnections.get(key);
            if (c != null) {
                c.close();
                clientConnections.remove(key);
            }
        }
    }

    /**
     * getClient synchronized and calls PreBuiltTransportClient
     *
     * @return {code null} is error
     */
    public static synchronized TransportClient getClient(Settings nodeSettings, List<ElasticsearchNode> nodes) {
        if (nodeSettings == null || nodes.size() == 0) {
            LOGGER.warn("Elasticsearch openConnection missing nodeSettings/nodes");
            nodeSettings = Settings.builder()
                    .put("client.transport.sniff", true).build();
        }
        TransportClient c = clientConnections.get(getHash(nodeSettings, nodes));
        if (c == null || !isAlive(c)) {
            try {
                c = new PreBuiltTransportClient(nodeSettings);
                for (ElasticsearchNode n : nodes) {
                    c.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(n.hostname), n.port));
                }
                clientConnections.put(getHash(nodeSettings, nodes), c);
                LOGGER.info("Creating connection {}", getHash(nodeSettings, nodes));
                return c;
            } catch (Exception error) {
                LOGGER.warn(
                        String.format("Elasticsearch getClient Cannot open ES Exception [%s: %s]",
                                error.getClass().getName(),
                                error.getMessage()),
                        error);
            }
            return null;
        } else {
            return c;
        }
    }
}
