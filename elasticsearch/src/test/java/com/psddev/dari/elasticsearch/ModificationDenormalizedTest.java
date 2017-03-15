package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Query;
import org.hamcrest.beans.HasPropertyWithValue;
import org.hamcrest.core.Every;
import org.hamcrest.core.Is;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

public class ModificationDenormalizedTest extends AbstractElasticTest {

    final static String value = "foo";
    final static String junkValue = "junk";
    final static List<String> values = Arrays.asList("foo", "bar", "baz");
    final static List<String> junkValues = Arrays.asList("abe", "lincoln", "president");

    protected Query<ModificationDenormalizedModel> query() {
        return Query.from(ModificationDenormalizedModel.class);
    }

    @After
    public void deleteModels() {
        Query.from(ModificationDenormalizedModel.class).deleteAll();
        Query.from(ElasticTag.class).deleteAll();
    }

    @Test
    public void testBasic() {
        ModificationDenormalizedModel test = new ModificationDenormalizedModel();
        test.setName("Test");
        test.save();

        Query query = Query.from(ModificationDenormalizedModel.class);
        List<ModificationDenormalizedModel> m = query.selectAll();

        assertThat(query.count(), is(1L));
        assertThat(m, (Every.everyItem(HasPropertyWithValue.hasProperty("name", Is.is("Test")))));
    }

    private List<ElasticTag> buildList(List<String> values) {
        List<ElasticTag> eList = new ArrayList<>();
        for (String v : values) {
            ElasticTag t = new ElasticTag();
            t.setName(v);
            t.save();
            eList.add(t);
        }
        return eList;
    }

    private Set<ElasticTag> buildSet(List<String> values) {
        Set<ElasticTag> eList = new HashSet<>();
        for (String v : values) {
            ElasticTag t = new ElasticTag();
            t.setName(v);
            t.save();
            eList.add(t);
        }
        return eList;
    }

    private ElasticTag buildValue(String value) {
        ElasticTag t = new ElasticTag();
        t.setName(value);
        t.save();
        return t;
    }

    @Test
    public void testSingleString() {
        List<ElasticTag> eList = buildList(values);
        Set<ElasticTag> eSet = buildSet(values);
        ElasticTag eValue = buildValue(value);
        List<ElasticTag> eListJunk = buildList(junkValues);
        Set<ElasticTag> eSetJunk = buildSet(junkValues);
        ElasticTag eValueJunk = buildValue(junkValue);

        ModificationDenormalizedModel test = new ModificationDenormalizedModel();
        test.setName(value);
        test.as(TaggableDenormalizedModification.class).setOtherTags(eList);
        test.as(TaggableDenormalizedModification.class).setName(value);
        test.as(TaggableDenormalizedModification.class).setPrimaryTag(eValue);
        test.as(TaggableDenormalizedModification.class).setOtherTagsSet(eSet);
        test.save();

        ModificationDenormalizedModel test2 = new ModificationDenormalizedModel();
        test2.setName(junkValue);
        test2.as(TaggableDenormalizedModification.class).setOtherTags(eListJunk);
        test2.as(TaggableDenormalizedModification.class).setName(junkValue);
        test2.as(TaggableDenormalizedModification.class).setPrimaryTag(eValueJunk);
        test2.as(TaggableDenormalizedModification.class).setOtherTagsSet(eSetJunk);
        test2.save();

        List<ModificationDenormalizedModel> junk = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTags/name = ?", "junk").selectAll();
        assertThat(junk, hasSize(0));

        List<ModificationDenormalizedModel> foo1 = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTags/name = ?", value).selectAll();
        assertThat(foo1, hasSize(1));
        assertThat(foo1.get(0).getName(), is(value));
        List<ElasticTag> foo1Other= foo1.get(0).as(TaggableDenormalizedModification.class).getOtherTags();
        assertThat(foo1Other.get(0).getName(), is(values.get(0)));
        assertThat(foo1Other.get(1).getName(), is(values.get(1)));
        assertThat(foo1Other.get(2).getName(), is(values.get(2)));

        List<ModificationDenormalizedModel> fooResult = Query.from(ModificationDenormalizedModel.class).where("tgd.primaryTag/name = ?", value).selectAll();
        assertThat(fooResult, hasSize(1));
        assertThat(foo1.get(0).getName(), is(value));

        List<ModificationDenormalizedModel> foo2Result = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTagsSet/name = ?", values).selectAll();
        assertThat(foo2Result, hasSize(1));
        assertThat(foo1.get(0).getName(), is(value));
    }

    @Test(expected = Query.NoIndexException.class)
    public void testNotIndexedModification() {
        ModificationDenormalizedModel test = new ModificationDenormalizedModel();
        test.save();

        Query.from(ModificationDenormalizedModel.class).where("tgd.name = ?", value).selectAll();
    }

    @Test
    public void testRefIdString() {
        List<ElasticTag> eList = buildList(values);
        Set<ElasticTag> eSet = buildSet(values);
        ElasticTag eValue = buildValue(value);

        ModificationDenormalizedModel test = new ModificationDenormalizedModel();
        test.setName(value);
        test.as(TaggableDenormalizedModification.class).setOtherTags(eList);
        test.as(TaggableDenormalizedModification.class).setName(value);
        test.as(TaggableDenormalizedModification.class).setPrimaryTag(eValue);
        test.as(TaggableDenormalizedModification.class).setOtherTagsSet(eSet);
        test.save();

        List<UUID> refs = new ArrayList<>();
        for (ElasticTag e: eList) {
            refs.add(e.getId());
        }

        List<ModificationDenormalizedModel> junk = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTags = ?", UUID.randomUUID()).selectAll();
        assertThat(junk, hasSize(0));

        List<ModificationDenormalizedModel> one = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTags = ?", refs.get(0)).selectAll();
        assertThat(one, hasSize(1));
        assertThat(one.get(0).getName(), is(value));
        List<ElasticTag> oneOther= one.get(0).as(TaggableDenormalizedModification.class).getOtherTags();
        assertThat(oneOther.get(0).getName(), is(values.get(0)));
        assertThat(oneOther.get(1).getName(), is(values.get(1)));
        assertThat(oneOther.get(2).getName(), is(values.get(2)));

        List<ModificationDenormalizedModel> many = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTags = ?", refs).selectAll();
        assertThat(many, hasSize(1));
        assertThat(one.get(0).getName(), is(value));

        List<UUID> refsSet = new ArrayList<>();
        for (ElasticTag e: eSet) {
            refsSet.add(e.getId());
        }

        List<ModificationDenormalizedModel> oneSet = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTagsSet = ?", refsSet.get(0)).selectAll();
        assertThat(oneSet, hasSize(1));
        assertThat(one.get(0).getName(), is(value));
        List<ModificationDenormalizedModel> manySet = Query.from(ModificationDenormalizedModel.class).where("tgd.otherTagsSet = ?", refsSet).selectAll();
        assertThat(manySet, hasSize(1));
        assertThat(one.get(0).getName(), is(value));

        UUID refValue = eValue.getId();

        List<ModificationDenormalizedModel> primary = Query.from(ModificationDenormalizedModel.class).where("tgd.primaryTag = ?", refValue).selectAll();
        assertThat(primary, hasSize(1));
        assertThat(one.get(0).getName(), is(value));
    }

}

