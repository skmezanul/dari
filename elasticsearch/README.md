# Brightspot Elasticsearch

## Installation

Make sure you install Elasticsearch 5.4.1 and Kibana 5.4.1

This works on Brightspot 3.3

```
brew install elasticsearch
brew install kibana
mvn clean install
```

## Configuration

Please setup conf/context.xml - Elastic can be the Database, or AggregateDatabase. As a Database it can replace MySQL. As an Aggregate it can replace Solr.

Example as AggregateDatabase:
```xml
    <!-- Database -->
    <Environment  name="dari/defaultDatabase"                                             type="java.lang.String" value="project" />
    <Environment  name="dari/database/project/class"                                      type="java.lang.String" value="com.psddev.dari.db.AggregateDatabase" />
    <Environment  name="dari/database/project/defaultDelegate"                            type="java.lang.String" value="sql" />

    <Environment  name="dari/database/project/delegate/sql/class"                         type="java.lang.String" value="com.psddev.dari.db.SqlDatabase" />
    <Environment  name="dari/database/project/delegate/sql/jdbcUrl"                       type="java.lang.String" value="jdbc:msyql://localhost:3306/project" />
    <Environment  name="dari/database/project/delegate/sql/enableReplicationCache"        type="java.lang.String" value="true" />
    <Environment  name="dari/database/project/delegate/sql/enableFunnelCache"             type="java.lang.String" value="false" />
    <Environment  name="dari/database/project/delegate/sql/indexSpatial"                  type="java.lang.String" value="false" />
    <!-- Database Resource Links, see server.xml for connection settings. -->
    <ResourceLink name="dari/database/project/delegate/sql/dataSource"                    type="javax.sql.DataSource" global="dari/database/project/delegate/sql/dataSource"  />
    <ResourceLink name="dari/database/project/delegate/sql/readDataSource"                type="javax.sql.DataSource" global="dari/database/project/delegate/sql/readDataSource"  />

    <Environment name="dari/database/project/delegate/elasticsearch/class"                type="java.lang.String" value="com.psddev.dari.elasticsearch.ElasticsearchDatabase" />
    <Environment name="dari/database/project/delegate/elasticsearch/clusterName"          type="java.lang.String" value="elasticsearch" />
    <Environment name="dari/database/project/delegate/elasticsearch/preferFilters"        type="java.lang.String" value="true" />
    <Environment name="dari/database/project/delegate/elasticsearch/clientTransportSniff" type="java.lang.String" value="false" />
    <Environment name="dari/database/project/delegate/elasticsearch/dfsQueryThenFetch"    type="java.lang.String" value="false" />
    <Environment name="dari/database/project/delegate/elasticsearch/indexName"            type="java.lang.String" value="index1" />
    <Environment name="dari/database/project/delegate/elasticsearch/shardsMax"            type="java.lang.String" value="5000" />
    <Environment name="dari/database/project/delegate/elasticsearch/defaultDataFieldType" type="java.lang.String" value="json" />
    <Environment name="dari/database/project/delegate/elasticsearch/dataTypesRaw"         type="java.lang.String" value="-* +com.psddev.dari.test.WriteModel " />
    <Environment name="dari/database/project/delegate/elasticsearch/subQueryResolveLimit" type="java.lang.String" value="1000" />
    <Environment name="dari/database/project/delegate/elasticsearch/1/clusterPort"        type="java.lang.String" value="9300" />
    <Environment name="dari/database/project/delegate/elasticsearch/1/clusterRestPort"    type="java.lang.String" value="9200" />
    <Environment name="dari/database/project/delegate/elasticsearch/1/clusterHostname"    type="java.lang.String" value="172.28.128.XXX" />
```
    
Some explanation:

- defaultDataFieldType - Can use "json" or "raw". "json" indicates storing data Stringified into Elastic. "raw" indicates storing as real objects.
- shardsMax - the default in Elastic is 1,000 shards max. This allows you to increase it.  When using defaultDataFieldType = "raw" each index is unique, due to conflicts.
- indexName - this is the indexName in Elastic - it is the begins with name. When using defaultDataFieldType = "raw" this is appended the typeId withoiut "-".
- clusterName - make sure this is correct for your cluster.
- the 1/clusterPort, 1/clusterRestPort, and 1/clusterHostname indicates the first Node in the Cluster. If you want you can include 2, 3... Or just the Load Balancer.
- When using defaultDataFieldType = "json" you can set some types to "raw" by setting SparseSet like below.
<Environment name="dari/database/project/delegate/elasticsearch/dataTypesRaw"          type="java.lang.String" value="-* +com.psddev.dari.test.WriteModel " />

- preferFilters - when not sorting, prefer filters since that Caches better - performance is better when set to true (default). Set to false to prefer queries for legacy operation.
- dfsQueryThenFetch - Queries are more accurate but might slow down depending on size of results and # of shards. Only use if results are not accurate.
- clientTransportSniff - setting the TransportClient client.transport.sniff setting (default is false)

Add the following to the `context.xml` file and fill out the `value`
attributes. Note that `s3Access` and `s3Secret` may be unnecessary
if Beam is configured to provide them.

### Modes

There are 2 modes: AggregateDatabase and as a Database provider. So it can replace both MySQL and Solr or just Solr.

## Querying

Use the following to Query and Use Elasticsearch

```java
import com.psddev.dari.elasticsearch.*;
import java.util.*;

public class Code {
    public static Object main() throws Throwable {
        ElasticsearchDatabase db = Database.Static.getFirst(ElasticsearchDatabase.class);
        return Query.fromAll().using(db).where("_type = 00000000-0000-0000-0000-000000000000").count();
    }
}
```
