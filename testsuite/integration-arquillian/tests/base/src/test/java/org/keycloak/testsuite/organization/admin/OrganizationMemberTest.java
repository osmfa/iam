/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.testsuite.organization.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.keycloak.models.OrganizationModel.USER_ORGANIZATION_ATTRIBUTE;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.Test;
import org.keycloak.admin.client.resource.OrganizationMemberResource;
import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.common.Profile.Feature;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.representations.userprofile.config.UPConfig.UnmanagedAttributePolicy;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;

@EnableFeature(Feature.ORGANIZATION)
public class OrganizationMemberTest extends AbstractOrganizationTest {

    @Test
    public void testUpdate() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        UserRepresentation expected = addMember(organization);

        expected.setFirstName("f");
        expected.setLastName("l");

        OrganizationMemberResource member = organization.members().member(expected.getId());

        try (Response response = member.update(expected)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        UserRepresentation existing = member.toRepresentation();
        assertEquals(expected.getId(), existing.getId());
        assertEquals(expected.getUsername(), existing.getUsername());
        assertEquals(expected.getEmail(), existing.getEmail());
        assertEquals(expected.getFirstName(), existing.getFirstName());
        assertEquals(expected.getLastName(), existing.getLastName());
    }

    @Test
    public void testFailSetUserOrganizationAttribute() {
        UPConfig upConfig = testRealm().users().userProfile().getConfiguration();
        upConfig.setUnmanagedAttributePolicy(UnmanagedAttributePolicy.ENABLED);
        testRealm().users().userProfile().update(upConfig);
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        UserRepresentation expected = new UserRepresentation();

        expected.setUsername(expected.getEmail());
        expected.singleAttribute(USER_ORGANIZATION_ATTRIBUTE, "invalid");

        try (Response response = organization.members().addMember(expected)) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertTrue(testRealm().users().search("u@o.org").isEmpty());
        }
    }

    @Test
    public void testFailSetEmailDomainOtherThanOrganizationDomain() {
        UPConfig upConfig = testRealm().users().userProfile().getConfiguration();
        upConfig.setUnmanagedAttributePolicy(UnmanagedAttributePolicy.ENABLED);
        testRealm().users().userProfile().update(upConfig);
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        UserRepresentation expected = new UserRepresentation();

        expected.setUsername(KeycloakModelUtils.generateId() + "@user.org");
        expected.setEmail(expected.getUsername());

        try (Response response = organization.members().addMember(expected)) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertTrue(testRealm().users().search(expected.getUsername()).isEmpty());
        }

        expected.setUsername(expected.getUsername().replace("@user.org", "@" + organizationName + ".org"));
        expected.setEmail(expected.getUsername());

        try (Response response = organization.members().addMember(expected)) {
            assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
            assertFalse(testRealm().users().search(expected.getUsername()).isEmpty());
        }
    }

    @Test
    public void testFailSetEmailDomainOtherThanOrganizationDomainViaUserApi() {
        RealmRepresentation representation = testRealm().toRepresentation();
        representation.setEditUsernameAllowed(true);
        testRealm().update(representation);
        OrganizationRepresentation organization = createOrganization();
        UserRepresentation member = addMember(testRealm().organizations().get(organization.getId()));

        member.setUsername(KeycloakModelUtils.generateId() + "@user.org");
        member.setEmail(member.getUsername());
        member.setFirstName("f");
        member.setLastName("l");
        member.setEnabled(true);

        try {
            testRealm().users().get(member.getId()).update(member);
            fail("Should fail because email domain does not match any from organization");
        } catch (BadRequestException expected) {

        }

        member.setUsername(member.getUsername().replace("@user.org", "@" + organizationName + ".org"));
        member.setEmail(member.getUsername());

        testRealm().users().get(member.getId()).update(member);
    }

    @Test
    public void testGet() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        UserRepresentation expected = addMember(organization);
        UserRepresentation existing = organization.members().member(expected.getId()).toRepresentation();
        assertEquals(expected.getId(), existing.getId());
        assertEquals(expected.getUsername(), existing.getUsername());
        assertEquals(expected.getEmail(), existing.getEmail());
    }

    @Test
    public void testGetMemberOrganization() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        UserRepresentation member = addMember(organization);
        OrganizationRepresentation expected = organization.toRepresentation();
        OrganizationRepresentation actual = organization.members().getOrganization(member.getId());
        assertNotNull(actual);
        assertEquals(expected.getId(), actual.getId());
    }

    @Test
    public void testGetAll() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        List<UserRepresentation> expected = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            expected.add(addMember(organization, "member-" + i + "@neworg.org"));
        }

        List<UserRepresentation> existing = organization.members().getAll();;
        assertFalse(existing.isEmpty());
        assertEquals(expected.size(), existing.size());
        for (UserRepresentation expectedRep : expected) {
            UserRepresentation existingRep = existing.stream().filter(member -> member.getId().equals(expectedRep.getId())).findAny().orElse(null);
            assertNotNull(existingRep);
            assertEquals(expectedRep.getId(), existingRep.getId());
            assertEquals(expectedRep.getUsername(), existingRep.getUsername());
            assertEquals(expectedRep.getEmail(), existingRep.getEmail());
            assertEquals(expectedRep.getFirstName(), existingRep.getFirstName());
            assertEquals(expectedRep.getLastName(), existingRep.getLastName());
        }
    }

    @Test
    public void testDelete() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        UserRepresentation expected = addMember(organization);
        OrganizationMemberResource member = organization.members().member(expected.getId());

        try (Response response = member.delete()) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        try {
            member.toRepresentation();
            fail("should be deleted");
        } catch (NotFoundException ignore) {}
    }

    @Test
    public void testDeleteMembersOnOrganizationRemoval() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        List<UserRepresentation> expected = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            expected.add(addMember(organization, "member-" + i + "@neworg.org"));
        }

        organization.delete().close();

        for (UserRepresentation member : expected) {
            try {
                organization.members().member(member.getId()).toRepresentation();
                fail("should be deleted");
            } catch (NotFoundException ignore) {}
        }

        for (UserRepresentation member : expected) {
            try {
                testRealm().users().get(member.getId()).toRepresentation();
                fail("should be deleted");
            } catch (NotFoundException ignore) {}
        }
    }

    @Test
    public void testDeleteGroupOnOrganizationRemoval() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        addMember(organization);

        assertTrue(testRealm().groups().groups().stream().anyMatch(group -> group.getName().startsWith("kc.org.")));

        organization.delete().close();

        assertFalse(testRealm().groups().groups().stream().anyMatch(group -> group.getName().startsWith("kc.org.")));
    }
}