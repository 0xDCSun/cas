package org.apereo.cas.support.openid.web.mvc;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.openid.OpenIdProtocolConstants;
import org.apereo.cas.ticket.proxy.ProxyHandler;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.validation.CasProtocolValidationSpecification;
import org.apereo.cas.validation.RequestedAuthenticationContextValidator;
import org.apereo.cas.validation.ServiceTicketValidationAuthorizersExecutionPlan;
import org.apereo.cas.web.AbstractServiceValidateController;
import org.apereo.cas.web.ServiceValidationViewFactory;
import org.apereo.cas.web.support.ArgumentExtractor;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.openid4java.message.ParameterList;
import org.openid4java.message.VerifyResponse;
import org.openid4java.server.ServerManager;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;

/**
 * An Openid controller that delegates to its own views on service validates.
 * This controller is part of the {@link org.apereo.cas.web.DelegatingController}.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
@Slf4j
public class OpenIdValidateController extends AbstractServiceValidateController {

    private final ServerManager serverManager;

    public OpenIdValidateController(final CasProtocolValidationSpecification validationSpecification,
                                    final AuthenticationSystemSupport authenticationSystemSupport,
                                    final ServicesManager servicesManager,
                                    final CentralAuthenticationService centralAuthenticationService,
                                    final ProxyHandler proxyHandler,
                                    final ArgumentExtractor argumentExtractor,
                                    final RequestedAuthenticationContextValidator requestedContextValidator,
                                    final String authnContextAttribute,
                                    final ServerManager serverManager,
                                    final ServiceTicketValidationAuthorizersExecutionPlan validationAuthorizers,
                                    final boolean renewEnabled,
                                    final ServiceValidationViewFactory validationViewFactory) {
        super(CollectionUtils.wrapSet(validationSpecification), validationAuthorizers,
            authenticationSystemSupport, servicesManager, centralAuthenticationService, proxyHandler,
            argumentExtractor, requestedContextValidator,
            authnContextAttribute, renewEnabled, validationViewFactory);
        this.serverManager = serverManager;
    }

    @Override
    public ModelAndView handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        val openIdMode = request.getParameter(OpenIdProtocolConstants.OPENID_MODE);
        if (StringUtils.equals(openIdMode, OpenIdProtocolConstants.CHECK_AUTHENTICATION)) {

            val requestParams = new ParameterList(request.getParameterMap());
            val message = (VerifyResponse) this.serverManager.verify(requestParams);

            val parameters = new HashMap<String, String>(message.getParameterMap());
            if (message.isSignatureVerified()) {
                LOGGER.debug("Signature verification request successful.");
                return new ModelAndView(getValidationViewFactory().getSuccessView(getClass()), parameters);
            }
            LOGGER.debug("Signature verification request unsuccessful.");
            return new ModelAndView(getValidationViewFactory().getFailureView(getClass()), parameters);
        }
        return super.handleRequestInternal(request, response);
    }

    @Override
    public boolean canHandle(final HttpServletRequest request, final HttpServletResponse response) {
        val openIdMode = request.getParameter(OpenIdProtocolConstants.OPENID_MODE);
        if (StringUtils.equals(openIdMode, OpenIdProtocolConstants.CHECK_AUTHENTICATION)) {
            LOGGER.info("Handling request. openid.mode : [{}]", openIdMode);
            return true;
        }
        LOGGER.info("Cannot handle request. openid.mode : [{}]", openIdMode);
        return false;
    }
}
