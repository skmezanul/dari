package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Recordable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StringElasticIndexModel extends AbstractElasticIndexModel<StringElasticIndexModel, String> {

    @Recordable.Indexed
    private String one;

    @Recordable.Indexed
    private Set<String> set;

    @Recordable.Indexed
    private List<String> list;

    @Recordable.Indexed
    private StringElasticIndexModel referenceOne;

    @Recordable.Indexed
    private Set<StringElasticIndexModel> referenceSet;

    @Recordable.Indexed
    private List<StringElasticIndexModel> referenceList;

    @Recordable.Embedded
    @Recordable.Indexed
    private StringElasticIndexModel embeddedOne;

    @Recordable.Embedded
    @Recordable.Indexed
    private Set<StringElasticIndexModel> embeddedSet;

    @Recordable.Embedded
    @Recordable.Indexed
    private List<StringElasticIndexModel> embeddedList;

    @Override
    public String getOne() {
        return one;
    }

    @Override
    public void setOne(String one) {
        this.one = one;
    }

    @Override
    public Set<String> getSet() {
        if (set == null) {
            set = new LinkedHashSet<>();
        }
        return set;
    }

    @Override
    public void setSet(Set<String> set) {
        this.set = set;
    }

    @Override
    public List<String> getList() {
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    @Override
    public void setList(List<String> list) {
        this.list = list;
    }

    @Override
    public StringElasticIndexModel getReferenceOne() {
        return referenceOne;
    }

    @Override
    public void setReferenceOne(StringElasticIndexModel referenceOne) {
        this.referenceOne = referenceOne;
    }

    @Override
    public Set<StringElasticIndexModel> getReferenceSet() {
        if (referenceSet == null) {
            referenceSet = new LinkedHashSet<>();
        }
        return referenceSet;
    }

    @Override
    public void setReferenceSet(Set<StringElasticIndexModel> referenceSet) {
        this.referenceSet = referenceSet;
    }

    @Override
    public List<StringElasticIndexModel> getReferenceList() {
        if (referenceList == null) {
            referenceList = new ArrayList<>();
        }
        return referenceList;
    }

    @Override
    public void setReferenceList(List<StringElasticIndexModel> referenceList) {
        this.referenceList = referenceList;
    }

    @Override
    public StringElasticIndexModel getEmbeddedOne() {
        return embeddedOne;
    }

    @Override
    public void setEmbeddedOne(StringElasticIndexModel embeddedOne) {
        this.embeddedOne = embeddedOne;
    }

    @Override
    public Set<StringElasticIndexModel> getEmbeddedSet() {
        if (embeddedSet == null) {
            embeddedSet = new LinkedHashSet<>();
        }
        return embeddedSet;
    }

    @Override
    public void setEmbeddedSet(Set<StringElasticIndexModel> embeddedSet) {
        this.embeddedSet = embeddedSet;
    }

    @Override
    public List<StringElasticIndexModel> getEmbeddedList() {
        if (embeddedList == null) {
            embeddedList = new ArrayList<>();
        }
        return embeddedList;
    }

    @Override
    public void setEmbeddedList(List<StringElasticIndexModel> embeddedList) {
        this.embeddedList = embeddedList;
    }
}
