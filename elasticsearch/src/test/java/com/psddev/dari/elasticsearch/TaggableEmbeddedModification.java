package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Modification;
import com.psddev.dari.db.Recordable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//@Modification.Classes({ModificationEmbeddedModel.class})
@Recordable.FieldInternalNamePrefix("tgd.")
public class TaggableEmbeddedModification extends Modification<TaggableEmbedded> {

    String name;

    @Indexed
    @Embedded
    private ElasticTag primaryTag;

    @Indexed
    @Embedded
    private List<ElasticTag> otherTags;

    @Indexed
    @Embedded
    private Set<ElasticTag> otherTagsSet;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ElasticTag getPrimaryTag() {
        return primaryTag;
    }

    public void setPrimaryTag(ElasticTag primaryTag) {
        this.primaryTag = primaryTag;
    }

    public List<ElasticTag> getOtherTags() {
        if (otherTags == null) {
            otherTags = new ArrayList<>();
        }
        return otherTags;
    }

    public void setOtherTags(List<ElasticTag> otherTags) {
        this.otherTags = otherTags;
    }

    public Set<ElasticTag> getOtherTagsSet() {
        if (otherTagsSet == null) {
            otherTagsSet = new HashSet<>();
        }
        return otherTagsSet;
    }

    public void setOtherTagsSet(Set<ElasticTag> otherTagsSet) {
        this.otherTagsSet = otherTagsSet;
    }
}
