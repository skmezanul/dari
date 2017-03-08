package com.psddev.dari.elasticsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchArticleElasticIndexModel extends AbstractElasticIndexModel<SearchArticleElasticIndexModel, String> {

    @Indexed
    private String one;

    @Indexed
    private Set<String> set;

    @Indexed
    private List<String> list;

    @Indexed
    private SearchArticleElasticIndexModel referenceOne;

    @Indexed
    private Set<SearchArticleElasticIndexModel> referenceSet;

    @Indexed
    private List<SearchArticleElasticIndexModel> referenceList;

    @Embedded
    @Indexed
    private SearchArticleElasticIndexModel embeddedOne;

    @Embedded
    @Indexed
    private Set<SearchArticleElasticIndexModel> embeddedSet;

    @Embedded
    @Indexed
    private List<SearchArticleElasticIndexModel> embeddedList;

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
    public SearchArticleElasticIndexModel getReferenceOne() {
        return referenceOne;
    }

    @Override
    public void setReferenceOne(SearchArticleElasticIndexModel referenceOne) {
        this.referenceOne = referenceOne;
    }

    @Override
    public Set<SearchArticleElasticIndexModel> getReferenceSet() {
        if (referenceSet == null) {
            referenceSet = new LinkedHashSet<>();
        }
        return referenceSet;
    }

    @Override
    public void setReferenceSet(Set<SearchArticleElasticIndexModel> referenceSet) {
        this.referenceSet = referenceSet;
    }

    @Override
    public List<SearchArticleElasticIndexModel> getReferenceList() {
        if (referenceList == null) {
            referenceList = new ArrayList<>();
        }
        return referenceList;
    }

    @Override
    public void setReferenceList(List<SearchArticleElasticIndexModel> referenceList) {
        this.referenceList = referenceList;
    }

    @Override
    public SearchArticleElasticIndexModel getEmbeddedOne() {
        return embeddedOne;
    }

    @Override
    public void setEmbeddedOne(SearchArticleElasticIndexModel embeddedOne) {
        this.embeddedOne = embeddedOne;
    }

    @Override
    public Set<SearchArticleElasticIndexModel> getEmbeddedSet() {
        if (embeddedSet == null) {
            embeddedSet = new LinkedHashSet<>();
        }
        return embeddedSet;
    }

    @Override
    public void setEmbeddedSet(Set<SearchArticleElasticIndexModel> embeddedSet) {
        this.embeddedSet = embeddedSet;
    }

    @Override
    public List<SearchArticleElasticIndexModel> getEmbeddedList() {
        if (embeddedList == null) {
            embeddedList = new ArrayList<>();
        }
        return embeddedList;
    }

    @Override
    public void setEmbeddedList(List<SearchArticleElasticIndexModel> embeddedList) {
        this.embeddedList = embeddedList;
    }
}
