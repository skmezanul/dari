package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Record;
import com.psddev.dari.db.Recordable;

@Recordable.FieldInternalNamePrefix("taggable.")
public class DenormalizedReferenceModel extends Record {

    private String name;

    @Indexed
    @Denormalized
    private ElasticTag indexedTag;

    @Denormalized
    private ElasticTag unindexedTag;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ElasticTag getIndexedTag() {
        return indexedTag;
    }

    public void setIndexedTag(ElasticTag indexedTag) {
        this.indexedTag = indexedTag;
    }

    public ElasticTag getUnindexedTag() {
        return unindexedTag;
    }

    public void setUnindexedTag(ElasticTag unindexedTag) {
        this.unindexedTag = unindexedTag;
    }
}
