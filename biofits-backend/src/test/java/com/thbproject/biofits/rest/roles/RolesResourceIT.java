package com.thbproject.biofits.rest.roles;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.thbproject.biofits.ItBase;
import com.thbproject.biofits.model.Role;
import com.thbproject.biofits.repository.RoleRepository;
import com.thbproject.biofits.rest.ApiConstants;

import io.restassured.http.ContentType;

public class RolesResourceIT extends ItBase {

    @Autowired
    private RoleRepository repository;

    private Role role1;

    private Role role2;

    @Before
    public void setup() throws Exception {
        super.setup();

        // create role1
        role1 = buildRole();
        role1 = repository.save(role1);
        // create role 2
        role2 = repository.save(buildRole());
    }

    @Override
    @After
    public void cleanup() throws Exception {
        repository.deleteAll();
        super.cleanup();
    }

    @Test
    public void create() {
        Role create = buildRole();
        int id = preLoadedGiven.contentType(ContentType.JSON).body(create).log().body().post(ApiConstants.ROLE_COLLECTION)
                .then().log().body().statusCode(200).extract().body().path("id");

        // check that the role has been saved
        Role actual = repository.findOne(Integer.toUnsignedLong(id));
        assertThat(actual, is(notNullValue()));
        assertThat(actual.getName(), is(equalTo(create.getName())));
        assertThat(actual.getDescription(), is(equalTo(create.getDescription())));
    }

    @Test
    public void createNameNull() {
        Role create = buildRole();
        create.setName(null);
        preLoadedGiven.contentType(ContentType.JSON).body(create).log().body().post(ApiConstants.ROLE_COLLECTION).then()
                .log().body().statusCode(400);
    }

    @Test
    public void createNameVide() {
        Role create = buildRole();
        create.setName("");
        preLoadedGiven.contentType(ContentType.JSON).body(create).log().body().post(ApiConstants.ROLE_COLLECTION).then()
                .log().body().statusCode(400);
    }

    @Test
    public void createMemeName() {
        Role create = buildRole();
        create.setName(role1.getName());
        preLoadedGiven.contentType(ContentType.JSON).body(create).log().body().post(ApiConstants.ROLE_COLLECTION).then()
                .log().body().statusCode(400);
    }

    @Test
    public void list() {
        preLoadedGiven.get(ApiConstants.ROLE_COLLECTION).then().log().body().statusCode(200).body("size()", is(equalTo(2)))
                .body("id", containsInAnyOrder(role1.getId().intValue(), role2.getId().intValue()));
    }

    @Test
    public void get() {
        preLoadedGiven.get(ApiConstants.ROLE_ITEM, role2.getId()).then().log().body().statusCode(200)
                .body("id", is(equalTo(role2.getId().intValue()))).body("name", is(equalTo(role2.getName())))
                .body("description", is(equalTo(role2.getDescription())));
    }

    @Test
    public void getNotFound() {
        preLoadedGiven.get(ApiConstants.ROLE_ITEM, random.nextLong()).then().statusCode(404);
    }

    @Test
    public void delete() {
        preLoadedGiven.delete(ApiConstants.ROLE_ITEM, role1.getId()).then().statusCode(200);

        // check that the role has been deleted
        Role actual = repository.findOne(role1.getId());
        assertThat(actual, is(nullValue()));

    }

    @Test
    public void deleteNotFound() {
        preLoadedGiven.delete(ApiConstants.ROLE_ITEM, random.nextLong()).then().statusCode(404);
    }

    @Test
    public void update() {
        Role update = buildRole();
        preLoadedGiven.contentType(ContentType.JSON).body(update).put(ApiConstants.ROLE_ITEM, role2.getId()).then().log()
                .body().statusCode(200);

        // check that the role has been saved
        Role actual = repository.findOne(role2.getId());
        assertThat(actual.getId(), is(equalTo(role2.getId())));
        assertThat(actual.getName(), is((equalTo(update.getName()))));
        assertThat(actual.getDescription(), is(equalTo(update.getDescription())));

    }

    @Test
    public void updateNotFound() {
        Role update = buildRole();
        preLoadedGiven.contentType(ContentType.JSON).body(update).put(ApiConstants.ROLE_ITEM, random.nextLong()).then()
                .log().body().statusCode(404);
    }

    @Test
    public void updateMemeName() {
        Role update = buildRole();
        update.setName(role1.getName());
        preLoadedGiven.contentType(ContentType.JSON).body(update).log().body().put(ApiConstants.ROLE_ITEM, role1.getId())
                .then().log().body().statusCode(200);

        // check that the role has been saved
        Role actual = repository.findOne(role1.getId());
        assertThat(actual.getName(), is((equalTo(update.getName()))));
        assertThat(actual.getDescription(), is(equalTo(update.getDescription())));
    }

    @Test
    public void updateNameExists() {
        Role update = buildRole();
        update.setName(role2.getName());
        preLoadedGiven.contentType(ContentType.JSON).body(update).log().body().put(ApiConstants.ROLE_ITEM, role1.getId())
                .then().log().body().statusCode(400);
    }

}