package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Location;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Region;
import com.psddev.dari.util.ObjectUtils;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RegionLocationTest extends AbstractElasticTest {

    @After
    public void deleteModels() {
        Query.from(SearchElasticModel.class).deleteAllImmediately();
        Query.from(RegionElasticIndexModel.class).deleteAllImmediately();
        Query.from(LocationElasticIndexModel.class).deleteAllImmediately();
    }

    @Test
    public void testGeoLocationQueryRegionWithRegion() throws Exception {

        RegionElasticIndexModel search = new RegionElasticIndexModel();
        search.setOne(Region.sphericalCircle(0.0d, 0.0d, 5d));
        search.save();

        List<RegionElasticIndexModel> fooResult = Query.from(RegionElasticIndexModel.class).where("one = ?", Region.sphericalCircle(0.0d, 0.0d, 5d)).selectAll();

        assertThat(fooResult, hasSize(1));

        assertThat(fooResult.get(0).getOne().getX(), is(0.0d));
        assertThat(fooResult.get(0).getOne().getY(), is(0.0d));
        assertThat(fooResult.get(0).getOne().getRadius(), is(5d));
        assertThat(fooResult.get(0).getOne().toString(), is(Region.sphericalCircle(0.0d, 0.0d, 5d).toString()));
        assertThat(ObjectUtils.toJson(fooResult.get(0).getOne()), is(ObjectUtils.toJson(Region.sphericalCircle(0.0d, 0.0d, 5d))));
    }

    @Test
    public void testGeoLocationQueryRegionWithLocation() throws Exception {

        RegionElasticIndexModel search = new RegionElasticIndexModel();
        search.setOne(Region.sphericalCircle(0.0d, 0.0d, 5d));
        search.save();

        List<RegionElasticIndexModel> fooResult = Query.from(RegionElasticIndexModel.class).where("one = ?", new Location(0d,0d)).selectAll();

        assertThat(fooResult, hasSize(1));

        assertThat(fooResult.get(0).getOne().getX(), is(0.0d));
        assertThat(fooResult.get(0).getOne().getY(), is(0.0d));
        assertThat(fooResult.get(0).getOne().getRadius(), is(5d));
        assertThat(fooResult.get(0).getOne().toString(), is(Region.sphericalCircle(0.0d, 0.0d, 5d).toString()));
        assertThat(ObjectUtils.toJson(fooResult.get(0).getOne()), is(ObjectUtils.toJson(Region.sphericalCircle(0.0d, 0.0d, 5d))));
    }

    @Test
    public void testGeoLocationQueryLocationWithRegion() throws Exception {

        LocationElasticIndexModel search = new LocationElasticIndexModel();
        search.setOne(new Location(0d,0d));
        search.save();

        List<LocationElasticIndexModel> fooResult = Query.from(LocationElasticIndexModel.class).where("one = ?", Region.sphericalCircle(0.0d, 0.0d, 5d)).selectAll();

        assertThat(fooResult, hasSize(1));

        assertThat(fooResult.get(0).getOne().getX(), is(0.0d));
        assertThat(fooResult.get(0).getOne().getY(), is(0.0d));
        assertThat(fooResult.get(0).getOne().toString(), is(new Location(0d,0d).toString()));
        assertThat(ObjectUtils.toJson(fooResult.get(0).getOne()), is(ObjectUtils.toJson(new Location(0d,0d))));
    }

    @Test
    public void testGeoLocationQueryLocationWithLocation() throws Exception {

        LocationElasticIndexModel search = new LocationElasticIndexModel();
        search.setOne(new Location(0d,0d));
        search.save();

        List<LocationElasticIndexModel> fooResult = Query.from(LocationElasticIndexModel.class).where("one = ?", new Location(0d, 0d)).selectAll();

        assertThat(fooResult, hasSize(1));

        assertThat(fooResult.get(0).getOne().getX(), is(0.0d));
        assertThat(fooResult.get(0).getOne().getY(), is(0.0d));
        assertThat(fooResult.get(0).getOne().toString(), is(new Location(0d,0d).toString()));
        assertThat(ObjectUtils.toJson(fooResult.get(0).getOne()), is(ObjectUtils.toJson(new Location(0d,0d))));
    }

    @Test
    public void testGeoLocationQueryString() throws Exception {
        SearchElasticModel search = new SearchElasticModel();
        search.setName("test");
        search.save();

        List<SearchElasticModel> fooResult = Query.from(SearchElasticModel.class).where("name = ?", "test").selectAll();

        assertThat(fooResult, hasSize(1));
        assertEquals("test", fooResult.get(0).getName());
    }
}

