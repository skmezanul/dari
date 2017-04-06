package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;
import com.psddev.dari.db.Recordable;

public class ElasticModel extends Record {
    @Recordable.Indexed
    String name;

    @Recordable.Indexed
    @ElasticsearchDatabase.ExcludeFromAny
    String desc;
}
