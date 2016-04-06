package org.openstack4j.api.identity.v3


import java.util.logging.Logger

import org.junit.Rule
import org.junit.rules.TestName
import org.openstack4j.api.AbstractSpec
import org.openstack4j.api.OSClient.OSClientV3
import org.openstack4j.model.common.Identifier
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.identity.v3.Role
import org.openstack4j.openstack.OSFactory

import spock.lang.IgnoreIf
import co.freeside.betamax.Betamax
import co.freeside.betamax.Recorder



class KeystoneRoleServiceSpec extends AbstractSpec {

    @Rule TestName KeystoneRoleServiceTest
    @Rule Recorder recorder = new Recorder(tapeRoot: new File(TAPEROOT+"identity.v3"))

    // additional attributes for role tests
    def static String ROLE_CRUD_NAME = "Role_CRUD"
    def static String ROLE_EMPTY_NAME = "roleNotFound"
    def static String ROLE_ONCE_NAME = "unassignedRole"
    def String ROLE_CRUD_ID

    static final boolean skipTest

    static {
        if(
        USER_ID == null ||
        AUTH_URL == null ||
        PASSWORD == null ||
        DOMAIN_ID == null ||
        PROJECT_ID == null  ) {

            skipTest = true
        }
        else{
            skipTest = false
        }
    }

    def setupSpec() {

        if( skipTest != true ) {
            Logger.getLogger(this.class.name).info("USER_ID: " + USER_ID)
            Logger.getLogger(this.class.name).info("AUTH_URL: " + AUTH_URL)
            Logger.getLogger(this.class.name).info("PASSWORD: " + PASSWORD)
            Logger.getLogger(this.class.name).info("DOMAIN_ID: " + DOMAIN_ID)
            Logger.getLogger(this.class.name).info("PROJECT_ID: " + PROJECT_ID)
        }
        else {
            Logger.getLogger(this.class.name).warning("Skipping integration-test cases because not all mandatory attributes are set.")
        }
    }

    def setup() {
        Logger.getLogger(this.class.name).info("-> Test: '$KeystoneRoleServiceTest.methodName'")
    }


    // ------------ RoleService Tests ------------

    @IgnoreIf({ skipTest })
    @Betamax(tape="roleService_all.tape")
    def "role service test cases: CRUD and grant, revoke, check, list userroles in domain and project context"() {

        given: "authenticated v3 OSClient"
        OSClientV3 os = OSFactory.builderV3()
                .endpoint(AUTH_URL)
                .credentials(USER_ID, PASSWORD)
                .scopeToDomain(Identifier.byId(DOMAIN_ID))
                .withConfig(CONFIG_PROXY_BETAMAX)
                .authenticate()

        when: "we try to get a a role by name 'null' "
        os.identity().roles().getByName(null)

        then: "a NPE is thrown"
        thrown NullPointerException

        when: "we create a role"
        Role role = os.identity().roles().create(ROLE_CRUD_NAME)

        then: "check the role was created successfully"
        role.getName() == ROLE_CRUD_NAME

        when: "we get the role by id"
        ROLE_CRUD_ID = role.getId()

        then: "the id shouldn't be null"
        ROLE_CRUD_ID != null

        when: "we try to get a role by name that is not found"
        List<? extends Role> roleList_empty = os.identity().roles().getByName(ROLE_EMPTY_NAME)

        then: "the role list should be empty"
        roleList_empty.isEmpty() == true

        when: "we try to get a role that is found once"
        List<? extends Role> roleList_oneEntry = os.identity().roles().getByName(ROLE_ONCE_NAME)

        then: "this role list should contain one item"
        roleList_oneEntry.size() == 1

        when: "we list all roles"
        List<? extends Role> roleList = os.identity().roles().list()

        then: "the list should not be empty and contain the recently created role"
        roleList.isEmpty() == false
        roleList.find { it.getName() == ROLE_CRUD_NAME}

        when: "we get a role by id"
        Role role_byId = os.identity().roles().get(ROLE_CRUD_ID)

        then: "check the correct role has been returned"
        role_byId.getId() == ROLE_CRUD_ID
        role_byId.getName() == ROLE_CRUD_NAME

        when: "we try to add a role to a user in project context where one or more attributes are 'null' "
        os.identity().roles().grantProjectUserRole(null, "fake", "fake")

        then: "we get a NPE "
        thrown NullPointerException

        when: "we try to add a role to a user in project context where one or more attributes are 'null' "
        os.identity().roles().grantProjectUserRole("fake", null , "fake")

        then: "we get a NPE "
        thrown NullPointerException

        when: "we try to add a role to a user in project context where one or more attributes are 'null' "
        os.identity().roles().grantProjectUserRole("fake", "fake", null)

        then: "we get a NPE "
        thrown NullPointerException

        when: "we grant a role to a user in project context"
        ActionResponse response_grantProjectUserRole_success = os.identity().roles().grantProjectUserRole(PROJECT_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)

        then: "this should be successful as indicated by the response"
        response_grantProjectUserRole_success.isSuccess() == true

        when: "we check that the user has the recently assigned role in project context"
        ActionResponse response_checkProjectUserRole_success = os.identity().roles().checkProjectUserRole(PROJECT_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)

        then: "the response should be successful while the user is assigned to that role in project context"
        response_checkProjectUserRole_success.isSuccess() == true

        when: "we revoke that role from the user"
        ActionResponse response_revokeProjectUserRole_success = os.identity().roles().revokeProjectUserRole(PROJECT_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)

        then: "the response should be successful"
        response_revokeProjectUserRole_success.isSuccess() == true

        //  TODO: this test is disabled due to a malformed response returned by OpenStack as described in issue #530
        //        when: "we check again if the user is assigned to that role"
        //        ActionResponse response_userProjectRole_fail = os.identity().roles().checkProjectUserRole(PROJECT_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)
        //
        //        then: "the response should indicate that we revoked the role before"
        //        response_userProjectRole_fail.isSuccess() == false

        when: "we try to add an nonexisting role to a user in project context"
        ActionResponse response_grantProjectUserRole_fail = os.identity().roles().grantProjectUserRole(PROJECT_ID, ROLE_CRUD_USER_ID, "nonExistingRoleId")

        then: "this results in a failing response"
        response_grantProjectUserRole_fail.isSuccess() == false

        when: "we try to revoke an existing project-role from a user that is not assigned to him"
        ActionResponse response_revokeProjectUserRole_fail = os.identity().roles().revokeProjectUserRole(PROJECT_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ANOTHER_ROLE_ID)

        then: "this results in a failing response"
        response_revokeProjectUserRole_fail.isSuccess() == false

        when: "we grant a role to a user in domain context"
        ActionResponse response_grantDomainUserRole_success = os.identity().roles().grantDomainUserRole(DOMAIN_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)

        then: "this should be successful as indicated by the response"
        response_grantDomainUserRole_success.isSuccess() == true

        when: "we check that the user has the recently assigned role in domain context"
        ActionResponse response_checkDomainUserRole_success = os.identity().roles().checkDomainUserRole(DOMAIN_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)

        then: "the response should be successful while the user is assigned to that role in domain context"
        response_checkDomainUserRole_success.isSuccess() == true

        when: "we revoke that role from the user"
        ActionResponse response_revokeDomainUserRole_success = os.identity().roles().revokeDomainUserRole(DOMAIN_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)

        then: "the response should be successful"
        response_revokeDomainUserRole_success.isSuccess() == true

        //  TODO: this test is disabled due to a malformed response returned by OpenStack as described in issue #530
        //        when: "we check again if the user is assigned to that role"
        //        ActionResponse response_checkDomainUserRole_fail = os.identity().roles().checkProjectUserRole(DOMAIN_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ID)
        //
        //        then: "the response should indicate that we revoked the role before"
        //        response_checkDomainUserRole_fail.isSuccess() == false

        when: "we try to add an nonexisting role to a user in domain context"
        ActionResponse response_grantDomainUserRole_fail = os.identity().roles().grantDomainUserRole(DOMAIN_ID, ROLE_CRUD_USER_ID, "nonExistingRoleId")

        then: "this results in a failing response"
        response_grantDomainUserRole_fail.isSuccess() == false

        when: "we try to revoke an existing domain-role from a user that is not assigned to him"
        ActionResponse response_revokeDomainUserRole_fail = os.identity().roles().revokeDomainUserRole(DOMAIN_ID, ROLE_CRUD_USER_ID, ROLE_CRUD_ANOTHER_ROLE_ID)

        then: "this results in a failing response"
        response_revokeDomainUserRole_fail.isSuccess() == false

        when: "we grant a role to a group in project context"
        ActionResponse response_grantProjectGroupRole_success = os.identity().roles().grantProjectGroupRole(PROJECT_ID, ROLE_CRUD_GROUP_ID, ROLE_CRUD_ID)

        then: "this should be successful and indicated by the response"
        response_grantProjectGroupRole_success.isSuccess() == true

        when: "we check that the group has the recently assigned role in project context"
        ActionResponse response_checkProjectGroupRole_success = os.identity().roles().checkProjectGroupRole(PROJECT_ID, ROLE_CRUD_GROUP_ID, ROLE_CRUD_ID)

        then: "this should be successful"
        response_grantProjectGroupRole_success.isSuccess() == true

        when: "revoke a role from a group in a project"
        ActionResponse response_revokeProjectGroupRole_success = os.identity().roles().revokeProjectGroupRole(PROJECT_ID, ROLE_CRUD_GROUP_ID, ROLE_CRUD_ID)

        then: "this should be successful"
        response_revokeProjectGroupRole_success.isSuccess() == true

        when: "grant a role to a group in domain context"
        ActionResponse response_grantDomainGroupRole_success = os.identity().roles().grantDomainGroupRole(DOMAIN_ID, ROLE_CRUD_GROUP_ID, ROLE_CRUD_ID)

        then: "this should be successful"
        response_grantDomainGroupRole_success.isSuccess() == true

        when: "check that the group has the recently assigned role in domain context"
        ActionResponse response_checkDomainGroupRole_success = os.identity().roles().checkDomainGroupRole(DOMAIN_ID, ROLE_CRUD_GROUP_ID, ROLE_CRUD_ID)

        then: "this should be successful"
        response_checkDomainGroupRole_success.isSuccess() == true

        when: "revoke a role from a group in domain"
        ActionResponse response_revokeDomainGroupRole_success = os.identity().roles().revokeDomainGroupRole(DOMAIN_ID, ROLE_CRUD_GROUP_ID, ROLE_CRUD_ID)

        then: "this should be successful"
        response_revokeDomainGroupRole_success.isSuccess() == true

        // TODO: Commented out, because currently the HttpClient used betamax v1.1.2 does not support HTTP PATCH method.
        //       See DefaultHttpRequestFactory used in co.freeside.betamax.proxy.handler.TargetConnector .
        //       Therefore update() is tested in core-test.
        //
        //        when: "an existing role is updated"
        //        Role role_setToUpdate = os.identity().roles().get(ROLE_CRUD_ID);
        //        if(role != null)
        //            Role updatedRole = os.identity().roles().update(role_setToUpdate.toBuilder().name(ROLE_NAME_UPDATED).build());
        //
        //        then: "verify the updated attributes"
        //        updatedRole.getId() == ROLE_CRUD_ID
        //        updatedRole.getName() == ROLE_NAME_UPDATED

        when: "delete a role"
        ActionResponse response_deleteRole_success = os.identity().roles().delete(ROLE_CRUD_ID)

        then: "this should be successful"
        response_deleteRole_success.isSuccess() == true
        
    }

}
