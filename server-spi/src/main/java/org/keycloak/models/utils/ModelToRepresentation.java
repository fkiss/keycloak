/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.utils;

import org.keycloak.component.ComponentModel;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionModel;
import org.keycloak.models.ClientTemplateModel;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.ModelException;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.models.RequiredCredentialModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserConsentModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserFederationMapperModel;
import org.keycloak.models.UserFederationProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;

import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.AuthDetailsRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientTemplateRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserConsentRepresentation;
import org.keycloak.representations.idm.UserFederationMapperRepresentation;
import org.keycloak.representations.idm.UserFederationProviderRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.keycloak.common.util.Time;

import java.util.*;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ModelToRepresentation {
    public static void buildGroupPath(StringBuilder sb, GroupModel group) {
        if (group.getParent() != null) {
            buildGroupPath(sb, group.getParent());
        }
        sb.append('/').append(group.getName());
    }

    public static String buildGroupPath(GroupModel group) {
        StringBuilder sb = new StringBuilder();
        buildGroupPath(sb, group);
        return sb.toString();
    }


    public static GroupRepresentation toRepresentation(GroupModel group, boolean full) {
        GroupRepresentation rep = new GroupRepresentation();
        rep.setId(group.getId());
        rep.setName(group.getName());
        rep.setPath(buildGroupPath(group));
        if (!full) return rep;
        // Role mappings
        Set<RoleModel> roles = group.getRoleMappings();
        List<String> realmRoleNames = new ArrayList<>();
        Map<String, List<String>> clientRoleNames = new HashMap<>();
        for (RoleModel role : roles) {
            if (role.getContainer() instanceof RealmModel) {
                realmRoleNames.add(role.getName());
            } else {
                ClientModel client = (ClientModel)role.getContainer();
                String clientId = client.getClientId();
                List<String> currentClientRoles = clientRoleNames.get(clientId);
                if (currentClientRoles == null) {
                    currentClientRoles = new ArrayList<>();
                    clientRoleNames.put(clientId, currentClientRoles);
                }

                currentClientRoles.add(role.getName());
            }
        }
        rep.setRealmRoles(realmRoleNames);
        rep.setClientRoles(clientRoleNames);
        Map<String, List<String>> attributes = group.getAttributes();
        rep.setAttributes(attributes);
        return rep;
    }

    public static List<GroupRepresentation> toGroupHierarchy(RealmModel realm, boolean full) {
        List<GroupRepresentation> hierarchy = new LinkedList<>();
        List<GroupModel> groups = realm.getTopLevelGroups();
        if (groups == null) return hierarchy;
        for (GroupModel group : groups) {
            GroupRepresentation rep = toGroupHierarchy(group, full);
            hierarchy.add(rep);
        }
        return hierarchy;
    }

    public static GroupRepresentation toGroupHierarchy(GroupModel group, boolean full) {
        GroupRepresentation rep = toRepresentation(group, full);
        List<GroupRepresentation> subGroups = new LinkedList<>();
        for (GroupModel subGroup : group.getSubGroups()) {
            subGroups.add(toGroupHierarchy(subGroup, full));
        }
        rep.setSubGroups(subGroups);
        return rep;
    }


    public static UserRepresentation toRepresentation(UserModel user) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId(user.getId());
        rep.setUsername(user.getUsername());
        rep.setCreatedTimestamp(user.getCreatedTimestamp());
        rep.setLastName(user.getLastName());
        rep.setFirstName(user.getFirstName());
        rep.setEmail(user.getEmail());
        rep.setEnabled(user.isEnabled());
        rep.setEmailVerified(user.isEmailVerified());
        rep.setTotp(user.isOtpEnabled());
        rep.setFederationLink(user.getFederationLink());

        List<String> reqActions = new ArrayList<String>();
        Set<String> requiredActions = user.getRequiredActions();
        for (String ra : requiredActions){
            reqActions.add(ra);
        }

        rep.setRequiredActions(reqActions);

        if (user.getAttributes() != null && !user.getAttributes().isEmpty()) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.putAll(user.getAttributes());
            rep.setAttributes(attrs);
        }
        return rep;
    }

    public static EventRepresentation toRepresentation(Event event) {
        EventRepresentation rep = new EventRepresentation();
        rep.setTime(event.getTime());
        rep.setType(event.getType().toString());
        rep.setRealmId(event.getRealmId());
        rep.setClientId(event.getClientId());
        rep.setUserId(event.getUserId());
        rep.setSessionId(event.getSessionId());
        rep.setIpAddress(event.getIpAddress());
        rep.setError(event.getError());
        rep.setDetails(event.getDetails());
        return rep;
    }

    public static AdminEventRepresentation toRepresentation(AdminEvent adminEvent) {
        AdminEventRepresentation rep = new AdminEventRepresentation();
        rep.setTime(adminEvent.getTime());
        rep.setRealmId(adminEvent.getRealmId());
        if (adminEvent.getAuthDetails() != null) {
            rep.setAuthDetails(toRepresentation(adminEvent.getAuthDetails()));
        }
        rep.setOperationType(adminEvent.getOperationType().toString());
        if (adminEvent.getResourceType() != null) {
            rep.setResourceType(adminEvent.getResourceType().toString());
        }
        rep.setResourcePath(adminEvent.getResourcePath());
        rep.setRepresentation(adminEvent.getRepresentation());
        rep.setError(adminEvent.getError());

        return rep;
    }

    public static AuthDetailsRepresentation toRepresentation(AuthDetails authDetails) {
        AuthDetailsRepresentation rep = new AuthDetailsRepresentation();
        rep.setRealmId(authDetails.getRealmId());
        rep.setClientId(authDetails.getClientId());
        rep.setUserId(authDetails.getUserId());
        rep.setIpAddress(authDetails.getIpAddress());
        return rep;
    }

    public static RoleRepresentation toRepresentation(RoleModel role) {
        RoleRepresentation rep = new RoleRepresentation();
        rep.setId(role.getId());
        rep.setName(role.getName());
        rep.setDescription(role.getDescription());
        rep.setScopeParamRequired(role.isScopeParamRequired());
        rep.setComposite(role.isComposite());
        rep.setClientRole(role.isClientRole());
        rep.setContainerId(role.getContainerId());
        return rep;
    }

    public static RealmRepresentation toRepresentation(RealmModel realm, boolean internal) {
        RealmRepresentation rep = new RealmRepresentation();
        rep.setId(realm.getId());
        rep.setRealm(realm.getName());
        rep.setDisplayName(realm.getDisplayName());
        rep.setDisplayNameHtml(realm.getDisplayNameHtml());
        rep.setEnabled(realm.isEnabled());
        rep.setNotBefore(realm.getNotBefore());
        rep.setSslRequired(realm.getSslRequired().name().toLowerCase());
        rep.setPublicKey(realm.getPublicKeyPem());
        if (internal) {
            rep.setPrivateKey(realm.getPrivateKeyPem());
            String privateKeyPem = realm.getPrivateKeyPem();
            if (realm.getCertificatePem() == null && privateKeyPem != null) {
                KeycloakModelUtils.generateRealmCertificate(realm);
            }
            rep.setCodeSecret(realm.getCodeSecret());
        }
        rep.setCertificate(realm.getCertificatePem());
        rep.setRegistrationAllowed(realm.isRegistrationAllowed());
        rep.setRegistrationEmailAsUsername(realm.isRegistrationEmailAsUsername());
        rep.setRememberMe(realm.isRememberMe());
        rep.setBruteForceProtected(realm.isBruteForceProtected());
        rep.setMaxFailureWaitSeconds(realm.getMaxFailureWaitSeconds());
        rep.setMinimumQuickLoginWaitSeconds(realm.getMinimumQuickLoginWaitSeconds());
        rep.setWaitIncrementSeconds(realm.getWaitIncrementSeconds());
        rep.setQuickLoginCheckMilliSeconds(realm.getQuickLoginCheckMilliSeconds());
        rep.setMaxDeltaTimeSeconds(realm.getMaxDeltaTimeSeconds());
        rep.setFailureFactor(realm.getFailureFactor());

        rep.setEventsEnabled(realm.isEventsEnabled());
        if (realm.getEventsExpiration() != 0) {
            rep.setEventsExpiration(realm.getEventsExpiration());
        }
        if (realm.getEventsListeners() != null) {
            rep.setEventsListeners(new LinkedList<String>(realm.getEventsListeners()));
        }
        if (realm.getEnabledEventTypes() != null) {
            rep.setEnabledEventTypes(new LinkedList<String>(realm.getEnabledEventTypes()));
        }

        rep.setAdminEventsEnabled(realm.isAdminEventsEnabled());
        rep.setAdminEventsDetailsEnabled(realm.isAdminEventsDetailsEnabled());

        rep.setVerifyEmail(realm.isVerifyEmail());
        rep.setResetPasswordAllowed(realm.isResetPasswordAllowed());
        rep.setEditUsernameAllowed(realm.isEditUsernameAllowed());
        rep.setRevokeRefreshToken(realm.isRevokeRefreshToken());
        rep.setAccessTokenLifespan(realm.getAccessTokenLifespan());
        rep.setAccessTokenLifespanForImplicitFlow(realm.getAccessTokenLifespanForImplicitFlow());
        rep.setSsoSessionIdleTimeout(realm.getSsoSessionIdleTimeout());
        rep.setSsoSessionMaxLifespan(realm.getSsoSessionMaxLifespan());
        rep.setOfflineSessionIdleTimeout(realm.getOfflineSessionIdleTimeout());
        rep.setAccessCodeLifespan(realm.getAccessCodeLifespan());
        rep.setAccessCodeLifespanUserAction(realm.getAccessCodeLifespanUserAction());
        rep.setAccessCodeLifespanLogin(realm.getAccessCodeLifespanLogin());
        rep.setSmtpServer(realm.getSmtpConfig());
        rep.setBrowserSecurityHeaders(realm.getBrowserSecurityHeaders());
        rep.setAccountTheme(realm.getAccountTheme());
        rep.setLoginTheme(realm.getLoginTheme());
        rep.setAdminTheme(realm.getAdminTheme());
        rep.setEmailTheme(realm.getEmailTheme());
        if (realm.getPasswordPolicy() != null) {
            rep.setPasswordPolicy(realm.getPasswordPolicy().toString());
        }
        OTPPolicy otpPolicy = realm.getOTPPolicy();
        rep.setOtpPolicyAlgorithm(otpPolicy.getAlgorithm());
        rep.setOtpPolicyPeriod(otpPolicy.getPeriod());
        rep.setOtpPolicyDigits(otpPolicy.getDigits());
        rep.setOtpPolicyInitialCounter(otpPolicy.getInitialCounter());
        rep.setOtpPolicyType(otpPolicy.getType());
        rep.setOtpPolicyLookAheadWindow(otpPolicy.getLookAheadWindow());
        if (realm.getBrowserFlow() != null) rep.setBrowserFlow(realm.getBrowserFlow().getAlias());
        if (realm.getRegistrationFlow() != null) rep.setRegistrationFlow(realm.getRegistrationFlow().getAlias());
        if (realm.getDirectGrantFlow() != null) rep.setDirectGrantFlow(realm.getDirectGrantFlow().getAlias());
        if (realm.getResetCredentialsFlow() != null) rep.setResetCredentialsFlow(realm.getResetCredentialsFlow().getAlias());
        if (realm.getClientAuthenticationFlow() != null) rep.setClientAuthenticationFlow(realm.getClientAuthenticationFlow().getAlias());

        List<String> defaultRoles = realm.getDefaultRoles();
        if (!defaultRoles.isEmpty()) {
            List<String> roleStrings = new ArrayList<String>();
            roleStrings.addAll(defaultRoles);
            rep.setDefaultRoles(roleStrings);
        }
        List<GroupModel> defaultGroups = realm.getDefaultGroups();
        if (!defaultGroups.isEmpty()) {
            List<String> groupPaths = new LinkedList<>();
            for (GroupModel group : defaultGroups) {
                groupPaths.add(ModelToRepresentation.buildGroupPath(group));
            }
            rep.setDefaultGroups(groupPaths);
        }

        List<RequiredCredentialModel> requiredCredentialModels = realm.getRequiredCredentials();
        if (requiredCredentialModels.size() > 0) {
            rep.setRequiredCredentials(new HashSet<String>());
            for (RequiredCredentialModel cred : requiredCredentialModels) {
                rep.getRequiredCredentials().add(cred.getType());
            }
        }

        List<UserFederationProviderModel> fedProviderModels = realm.getUserFederationProviders();
        if (fedProviderModels.size() > 0) {
            List<UserFederationProviderRepresentation> fedProviderReps = new ArrayList<UserFederationProviderRepresentation>();
            for (UserFederationProviderModel model : fedProviderModels) {
                UserFederationProviderRepresentation fedProvRep = toRepresentation(model);
                fedProviderReps.add(fedProvRep);
            }
            rep.setUserFederationProviders(fedProviderReps);
        }

        for (UserFederationMapperModel mapper : realm.getUserFederationMappers()) {
            rep.addUserFederationMapper(toRepresentation(realm, mapper));
        }

        for (IdentityProviderModel provider : realm.getIdentityProviders()) {
            rep.addIdentityProvider(toRepresentation(realm, provider));
        }

        for (IdentityProviderMapperModel mapper : realm.getIdentityProviderMappers()) {
            rep.addIdentityProviderMapper(toRepresentation(mapper));
        }

        rep.setInternationalizationEnabled(realm.isInternationalizationEnabled());
        if(realm.getSupportedLocales() != null){
            rep.setSupportedLocales(new HashSet<String>());
            rep.getSupportedLocales().addAll(realm.getSupportedLocales());
        }
        rep.setDefaultLocale(realm.getDefaultLocale());
        if (internal) {
            exportAuthenticationFlows(realm, rep);
            exportRequiredActions(realm, rep);
            exportGroups(realm, rep);
        }
        return rep;
    }

    public static void exportGroups(RealmModel realm, RealmRepresentation rep) {
        List<GroupRepresentation> groups = toGroupHierarchy(realm, true);
        rep.setGroups(groups);
    }

    public static void exportAuthenticationFlows(RealmModel realm, RealmRepresentation rep) {
        rep.setAuthenticationFlows(new LinkedList<AuthenticationFlowRepresentation>());
        rep.setAuthenticatorConfig(new LinkedList<AuthenticatorConfigRepresentation>());

        List<AuthenticationFlowModel> authenticationFlows = new ArrayList<>(realm.getAuthenticationFlows());
        //ensure consistent ordering of authenticationFlows.
        Collections.sort(authenticationFlows, new Comparator<AuthenticationFlowModel>() {
            @Override
            public int compare(AuthenticationFlowModel left, AuthenticationFlowModel right) {
                return left.getAlias().compareTo(right.getAlias());
            }
        });

        for (AuthenticationFlowModel model : authenticationFlows) {
            AuthenticationFlowRepresentation flowRep = toRepresentation(realm, model);
            rep.getAuthenticationFlows().add(flowRep);
        }

        List<AuthenticatorConfigModel> authenticatorConfigs = new ArrayList<>(realm.getAuthenticatorConfigs());
        //ensure consistent ordering of authenticatorConfigs.
        Collections.sort(authenticatorConfigs, new Comparator<AuthenticatorConfigModel>() {
            @Override
            public int compare(AuthenticatorConfigModel left, AuthenticatorConfigModel right) {
                return left.getAlias().compareTo(right.getAlias());
            }
        });

        for (AuthenticatorConfigModel model : authenticatorConfigs) {
            rep.getAuthenticatorConfig().add(toRepresentation(model));
        }

    }

    public static void exportRequiredActions(RealmModel realm, RealmRepresentation rep) {

        rep.setRequiredActions(new LinkedList<RequiredActionProviderRepresentation>());

        List<RequiredActionProviderModel> requiredActionProviders = realm.getRequiredActionProviders();
        List<RequiredActionProviderModel> copy = new LinkedList<>();
        copy.addAll(requiredActionProviders);
        requiredActionProviders = copy;
        //ensure consistent ordering of requiredActionProviders.
        Collections.sort(requiredActionProviders, new Comparator<RequiredActionProviderModel>() {
            @Override
            public int compare(RequiredActionProviderModel left, RequiredActionProviderModel right) {
                return left.getAlias().compareTo(right.getAlias());
            }
        });

        for (RequiredActionProviderModel model : requiredActionProviders) {
            RequiredActionProviderRepresentation action = toRepresentation(model);
            rep.getRequiredActions().add(action);
        }
    }


    public static RealmEventsConfigRepresentation toEventsConfigReprensetation(RealmModel realm) {
        RealmEventsConfigRepresentation rep = new RealmEventsConfigRepresentation();
        rep.setEventsEnabled(realm.isEventsEnabled());

        if (realm.getEventsExpiration() != 0) {
            rep.setEventsExpiration(realm.getEventsExpiration());
        }

        if (realm.getEventsListeners() != null) {
            rep.setEventsListeners(new LinkedList<>(realm.getEventsListeners()));
        }

        if(realm.getEnabledEventTypes() != null) {
            rep.setEnabledEventTypes(new LinkedList<>(realm.getEnabledEventTypes()));
        }

        rep.setAdminEventsEnabled(realm.isAdminEventsEnabled());

        rep.setAdminEventsDetailsEnabled(realm.isAdminEventsDetailsEnabled());

        return rep;
    }

    public static CredentialRepresentation toRepresentation(UserCredentialModel cred) {
        CredentialRepresentation rep = new CredentialRepresentation();
        rep.setType(CredentialRepresentation.SECRET);
        rep.setValue(cred.getValue());
        return rep;
    }

    public static FederatedIdentityRepresentation toRepresentation(FederatedIdentityModel socialLink) {
        FederatedIdentityRepresentation rep = new FederatedIdentityRepresentation();
        rep.setUserName(socialLink.getUserName());
        rep.setIdentityProvider(socialLink.getIdentityProvider());
        rep.setUserId(socialLink.getUserId());
        return rep;
    }

    public static UserSessionRepresentation toRepresentation(UserSessionModel session) {
        UserSessionRepresentation rep = new UserSessionRepresentation();
        rep.setId(session.getId());
        rep.setStart(Time.toMillis(session.getStarted()));
        rep.setLastAccess(Time.toMillis(session.getLastSessionRefresh()));
        rep.setUsername(session.getUser().getUsername());
        rep.setUserId(session.getUser().getId());
        rep.setIpAddress(session.getIpAddress());
        for (ClientSessionModel clientSession : session.getClientSessions()) {
            ClientModel client = clientSession.getClient();
            rep.getClients().put(client.getId(), client.getClientId());
        }
        return rep;
    }

    public static ClientTemplateRepresentation toRepresentation(ClientTemplateModel clientModel) {
        ClientTemplateRepresentation rep = new ClientTemplateRepresentation();
        rep.setId(clientModel.getId());
        rep.setName(clientModel.getName());
        rep.setDescription(clientModel.getDescription());
        rep.setProtocol(clientModel.getProtocol());
        if (!clientModel.getProtocolMappers().isEmpty()) {
            List<ProtocolMapperRepresentation> mappings = new LinkedList<>();
            for (ProtocolMapperModel model : clientModel.getProtocolMappers()) {
                mappings.add(toRepresentation(model));
            }
            rep.setProtocolMappers(mappings);
        }
        rep.setFullScopeAllowed(clientModel.isFullScopeAllowed());

        return rep;
    }


    public static ClientRepresentation toRepresentation(ClientModel clientModel) {
        ClientRepresentation rep = new ClientRepresentation();
        rep.setId(clientModel.getId());
        rep.setClientId(clientModel.getClientId());
        rep.setName(clientModel.getName());
        rep.setDescription(clientModel.getDescription());
        rep.setEnabled(clientModel.isEnabled());
        rep.setAdminUrl(clientModel.getManagementUrl());
        rep.setPublicClient(clientModel.isPublicClient());
        rep.setFrontchannelLogout(clientModel.isFrontchannelLogout());
        rep.setProtocol(clientModel.getProtocol());
        rep.setAttributes(clientModel.getAttributes());
        rep.setFullScopeAllowed(clientModel.isFullScopeAllowed());
        rep.setBearerOnly(clientModel.isBearerOnly());
        rep.setConsentRequired(clientModel.isConsentRequired());
        rep.setStandardFlowEnabled(clientModel.isStandardFlowEnabled());
        rep.setImplicitFlowEnabled(clientModel.isImplicitFlowEnabled());
        rep.setDirectAccessGrantsEnabled(clientModel.isDirectAccessGrantsEnabled());
        rep.setServiceAccountsEnabled(clientModel.isServiceAccountsEnabled());
        rep.setSurrogateAuthRequired(clientModel.isSurrogateAuthRequired());
        rep.setRootUrl(clientModel.getRootUrl());
        rep.setBaseUrl(clientModel.getBaseUrl());
        rep.setNotBefore(clientModel.getNotBefore());
        rep.setNodeReRegistrationTimeout(clientModel.getNodeReRegistrationTimeout());
        rep.setClientAuthenticatorType(clientModel.getClientAuthenticatorType());
        if (clientModel.getClientTemplate() != null) rep.setClientTemplate(clientModel.getClientTemplate().getName());

        Set<String> redirectUris = clientModel.getRedirectUris();
        if (redirectUris != null) {
            rep.setRedirectUris(new LinkedList<>(redirectUris));
        }

        Set<String> webOrigins = clientModel.getWebOrigins();
        if (webOrigins != null) {
            rep.setWebOrigins(new LinkedList<>(webOrigins));
        }

        if (!clientModel.getDefaultRoles().isEmpty()) {
            rep.setDefaultRoles(clientModel.getDefaultRoles().toArray(new String[0]));
        }

        if (!clientModel.getRegisteredNodes().isEmpty()) {
            rep.setRegisteredNodes(new HashMap<>(clientModel.getRegisteredNodes()));
        }

        if (!clientModel.getProtocolMappers().isEmpty()) {
            List<ProtocolMapperRepresentation> mappings = new LinkedList<>();
            for (ProtocolMapperModel model : clientModel.getProtocolMappers()) {
                mappings.add(toRepresentation(model));
            }
            rep.setProtocolMappers(mappings);
        }
        rep.setUseTemplateMappers(clientModel.useTemplateMappers());
        rep.setUseTemplateConfig(clientModel.useTemplateConfig());
        rep.setUseTemplateScope(clientModel.useTemplateScope());

        return rep;
    }

    public static UserFederationProviderRepresentation toRepresentation(UserFederationProviderModel model) {
        UserFederationProviderRepresentation rep = new UserFederationProviderRepresentation();
        rep.setId(model.getId());
        rep.setConfig(model.getConfig());
        rep.setProviderName(model.getProviderName());
        rep.setPriority(model.getPriority());
        rep.setDisplayName(model.getDisplayName());
        rep.setFullSyncPeriod(model.getFullSyncPeriod());
        rep.setChangedSyncPeriod(model.getChangedSyncPeriod());
        rep.setLastSync(model.getLastSync());
        return rep;
    }

     public static UserFederationMapperRepresentation toRepresentation(RealmModel realm, UserFederationMapperModel model) {
        UserFederationMapperRepresentation rep = new UserFederationMapperRepresentation();
        rep.setId(model.getId());
        rep.setName(model.getName());
        rep.setFederationMapperType(model.getFederationMapperType());
        Map<String, String> config = new HashMap<String, String>();
        config.putAll(model.getConfig());
        rep.setConfig(config);

        UserFederationProviderModel fedProvider = KeycloakModelUtils.findUserFederationProviderById(model.getFederationProviderId(), realm);
        if (fedProvider == null) {
            throw new ModelException("Couldn't find federation provider with ID " + model.getId());
        }
        rep.setFederationProviderDisplayName(fedProvider.getDisplayName());

        return rep;
    }

    public static IdentityProviderRepresentation toRepresentation(RealmModel realm, IdentityProviderModel identityProviderModel) {
        IdentityProviderRepresentation providerRep = new IdentityProviderRepresentation();

        providerRep.setInternalId(identityProviderModel.getInternalId());
        providerRep.setProviderId(identityProviderModel.getProviderId());
        providerRep.setAlias(identityProviderModel.getAlias());
        providerRep.setEnabled(identityProviderModel.isEnabled());
        providerRep.setStoreToken(identityProviderModel.isStoreToken());
        providerRep.setTrustEmail(identityProviderModel.isTrustEmail());
        providerRep.setAuthenticateByDefault(identityProviderModel.isAuthenticateByDefault());
        providerRep.setConfig(identityProviderModel.getConfig());
        providerRep.setAddReadTokenRoleOnCreate(identityProviderModel.isAddReadTokenRoleOnCreate());

        String firstBrokerLoginFlowId = identityProviderModel.getFirstBrokerLoginFlowId();
        if (firstBrokerLoginFlowId != null) {
            AuthenticationFlowModel flow = realm.getAuthenticationFlowById(firstBrokerLoginFlowId);
            if (flow == null) {
                throw new ModelException("Couldn't find authentication flow with id " + firstBrokerLoginFlowId);
            }
            providerRep.setFirstBrokerLoginFlowAlias(flow.getAlias());
        }

        String postBrokerLoginFlowId = identityProviderModel.getPostBrokerLoginFlowId();
        if (postBrokerLoginFlowId != null) {
            AuthenticationFlowModel flow = realm.getAuthenticationFlowById(postBrokerLoginFlowId);
            if (flow == null) {
                throw new ModelException("Couldn't find authentication flow with id " + postBrokerLoginFlowId);
            }
            providerRep.setPostBrokerLoginFlowAlias(flow.getAlias());
        }

        return providerRep;
    }

    public static ProtocolMapperRepresentation toRepresentation(ProtocolMapperModel model) {
        ProtocolMapperRepresentation rep = new ProtocolMapperRepresentation();
        rep.setId(model.getId());
        rep.setProtocol(model.getProtocol());
        Map<String, String> config = new HashMap<String, String>();
        config.putAll(model.getConfig());
        rep.setConfig(config);
        rep.setName(model.getName());
        rep.setProtocolMapper(model.getProtocolMapper());
        rep.setConsentText(model.getConsentText());
        rep.setConsentRequired(model.isConsentRequired());
        return rep;
    }

    public static IdentityProviderMapperRepresentation toRepresentation(IdentityProviderMapperModel model) {
        IdentityProviderMapperRepresentation rep = new IdentityProviderMapperRepresentation();
        rep.setId(model.getId());
        rep.setIdentityProviderMapper(model.getIdentityProviderMapper());
        rep.setIdentityProviderAlias(model.getIdentityProviderAlias());
        Map<String, String> config = new HashMap<String, String>();
        config.putAll(model.getConfig());
        rep.setConfig(config);
        rep.setName(model.getName());
        return rep;
    }

    public static UserConsentRepresentation toRepresentation(UserConsentModel model) {
        String clientId = model.getClient().getClientId();

        Map<String, List<String>> grantedProtocolMappers = new HashMap<String, List<String>>();
        for (ProtocolMapperModel protocolMapper : model.getGrantedProtocolMappers()) {
            String protocol = protocolMapper.getProtocol();
            List<String> currentProtocolMappers = grantedProtocolMappers.get(protocol);
            if (currentProtocolMappers == null) {
                currentProtocolMappers = new LinkedList<String>();
                grantedProtocolMappers.put(protocol, currentProtocolMappers);
            }
            currentProtocolMappers.add(protocolMapper.getName());
        }

        List<String> grantedRealmRoles = new LinkedList<String>();
        Map<String, List<String>> grantedClientRoles = new HashMap<String, List<String>>();
        for (RoleModel role : model.getGrantedRoles()) {
            if (role.getContainer() instanceof RealmModel) {
                grantedRealmRoles.add(role.getName());
            } else {
                ClientModel client2 = (ClientModel) role.getContainer();

                String clientId2 = client2.getClientId();
                List<String> currentClientRoles = grantedClientRoles.get(clientId2);
                if (currentClientRoles == null) {
                    currentClientRoles = new LinkedList<String>();
                    grantedClientRoles.put(clientId2, currentClientRoles);
                }
                currentClientRoles.add(role.getName());
            }
        }


        UserConsentRepresentation consentRep = new UserConsentRepresentation();
        consentRep.setClientId(clientId);
        consentRep.setGrantedProtocolMappers(grantedProtocolMappers);
        consentRep.setGrantedRealmRoles(grantedRealmRoles);
        consentRep.setGrantedClientRoles(grantedClientRoles);
        return consentRep;
    }

    public static AuthenticationFlowRepresentation  toRepresentation(RealmModel realm, AuthenticationFlowModel model) {
        AuthenticationFlowRepresentation rep = new AuthenticationFlowRepresentation();
        rep.setId(model.getId());
        rep.setBuiltIn(model.isBuiltIn());
        rep.setTopLevel(model.isTopLevel());
        rep.setProviderId(model.getProviderId());
        rep.setAlias(model.getAlias());
        rep.setDescription(model.getDescription());
        rep.setAuthenticationExecutions(new LinkedList<AuthenticationExecutionExportRepresentation>());
        for (AuthenticationExecutionModel execution : realm.getAuthenticationExecutions(model.getId())) {
            rep.getAuthenticationExecutions().add(toRepresentation(realm, execution));
        }
        return rep;

    }

    public static AuthenticationExecutionExportRepresentation toRepresentation(RealmModel realm, AuthenticationExecutionModel model) {
        AuthenticationExecutionExportRepresentation rep = new AuthenticationExecutionExportRepresentation();
        if (model.getAuthenticatorConfig() != null) {
            AuthenticatorConfigModel config = realm.getAuthenticatorConfigById(model.getAuthenticatorConfig());
            rep.setAuthenticatorConfig(config.getAlias());
        }
        rep.setAuthenticator(model.getAuthenticator());
        rep.setAutheticatorFlow(model.isAuthenticatorFlow());
        if (model.getFlowId() != null) {
            AuthenticationFlowModel flow = realm.getAuthenticationFlowById(model.getFlowId());
            rep.setFlowAlias(flow.getAlias());
       }
        rep.setPriority(model.getPriority());
        rep.setRequirement(model.getRequirement().name());
        return rep;
    }

    public static AuthenticatorConfigRepresentation toRepresentation(AuthenticatorConfigModel model) {
        AuthenticatorConfigRepresentation rep = new AuthenticatorConfigRepresentation();
        rep.setId(model.getId());
        rep.setAlias(model.getAlias());
        rep.setConfig(model.getConfig());
        return rep;
    }

    public static RequiredActionProviderRepresentation toRepresentation(RequiredActionProviderModel model) {
        RequiredActionProviderRepresentation rep = new RequiredActionProviderRepresentation();
        rep.setAlias(model.getAlias());
        rep.setDefaultAction(model.isDefaultAction());
        rep.setEnabled(model.isEnabled());
        rep.setConfig(model.getConfig());
        rep.setName(model.getName());
        rep.setProviderId(model.getProviderId());
        return rep;
    }

    public static List<ConfigPropertyRepresentation> toRepresentation(List<ProviderConfigProperty> configProperties) {
        List<ConfigPropertyRepresentation> propertiesRep = new LinkedList<>();
        for (ProviderConfigProperty prop : configProperties) {
            ConfigPropertyRepresentation propRep = new ConfigPropertyRepresentation();
            propRep.setName(prop.getName());
            propRep.setLabel(prop.getLabel());
            propRep.setType(prop.getType());
            propRep.setDefaultValue(prop.getDefaultValue());
            propRep.setHelpText(prop.getHelpText());
            propertiesRep.add(propRep);
        }
        return propertiesRep;
    }

    public static ComponentRepresentation toRepresentation(ComponentModel component) {
        ComponentRepresentation rep = new ComponentRepresentation();
        rep.setId(component.getId());
        rep.setName(component.getName());
        rep.setProviderId(component.getProviderId());
        rep.setProviderType(component.getProviderType());
        rep.setConfig(component.getConfig());
        return rep;
    }
}
