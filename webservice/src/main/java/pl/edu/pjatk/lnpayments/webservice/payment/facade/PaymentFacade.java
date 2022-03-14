package pl.edu.pjatk.lnpayments.webservice.payment.facade;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import pl.edu.pjatk.lnpayments.webservice.auth.service.UserService;
import pl.edu.pjatk.lnpayments.webservice.common.entity.User;
import pl.edu.pjatk.lnpayments.webservice.common.service.PropertyService;
import pl.edu.pjatk.lnpayments.webservice.payment.model.PaymentInfo;
import pl.edu.pjatk.lnpayments.webservice.payment.model.entity.Payment;
import pl.edu.pjatk.lnpayments.webservice.payment.model.entity.PaymentStatus;
import pl.edu.pjatk.lnpayments.webservice.payment.repository.enums.SearchableField;
import pl.edu.pjatk.lnpayments.webservice.payment.resource.PaymentSocketController;
import pl.edu.pjatk.lnpayments.webservice.payment.resource.dto.PaymentDetailsRequest;
import pl.edu.pjatk.lnpayments.webservice.payment.service.InvoiceService;
import pl.edu.pjatk.lnpayments.webservice.payment.service.NodeDetailsService;
import pl.edu.pjatk.lnpayments.webservice.payment.service.PaymentDataService;
import pl.edu.pjatk.lnpayments.webservice.payment.service.TokenService;
import pl.edu.pjatk.lnpayments.webservice.payment.task.PaymentStatusUpdateTask;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Service
public class PaymentFacade {

    private final InvoiceService invoiceService;
    private final PropertyService propertyService;
    private final PaymentDataService paymentDataService;
    private final NodeDetailsService nodeDetailsService;
    private final TokenService tokenService;
    private final PaymentSocketController paymentSocketController;
    private final UserService userService;
    private final TaskScheduler scheduler;

    @Autowired
    PaymentFacade(InvoiceService invoiceService,
                  PropertyService propertyService,
                  PaymentDataService paymentDataService,
                  NodeDetailsService nodeDetailsService,
                  TokenService tokenService,
                  PaymentSocketController paymentSocketController,
                  UserService userService,
                  @Qualifier("threadPoolTaskScheduler") TaskScheduler scheduler) {
        this.invoiceService = invoiceService;
        this.propertyService = propertyService;
        this.paymentDataService = paymentDataService;
        this.nodeDetailsService = nodeDetailsService;
        this.tokenService = tokenService;
        this.paymentSocketController = paymentSocketController;
        this.userService = userService;
        this.scheduler = scheduler;
    }

    public PaymentInfo buildInfoResponse(Optional<String> email) {
        Collection<Payment> pendingPayments = email.isPresent() ?
                paymentDataService.findPendingPaymentsByUser(email.get()):
                Collections.emptyList();
        return PaymentInfo.builder()
                .nodeUrl(nodeDetailsService.getNodeUrl())
                .description(propertyService.getDescription())
                .price(propertyService.getPrice())
                .pendingPayments(pendingPayments)
                .build();
    }

    public Payment createNewPayment(PaymentDetailsRequest paymentDetailsRequest, String email) {
        int paymentExpiryInSeconds = propertyService.getPaymentExpiryInSeconds();
        int price = propertyService.getPrice();
        int numberOfTokens = paymentDetailsRequest.getNumberOfTokens();
        User user = userService.findUserByEmail(email);
        String paymentRequest = invoiceService.createInvoice(
                numberOfTokens,
                price,
                paymentExpiryInSeconds,
                propertyService.getInvoiceMemo()
        );
        Payment payment = Payment.builder()
                .paymentRequest(paymentRequest)
                .numberOfTokens(numberOfTokens)
                .price(price)
                .expiryInSeconds(paymentExpiryInSeconds)
                .paymentStatus(PaymentStatus.PENDING)
                .user(user)
                .build();
        Payment paymentEntity = paymentDataService.savePayment(payment);
        scheduler.schedule(new PaymentStatusUpdateTask(paymentEntity, paymentDataService), paymentEntity.getExpiry());
        return paymentEntity;
    }

    @Transactional
    public void finalizePayment(String paymentRequest) {
        Payment payment = paymentDataService.findPaymentByRequest(paymentRequest);
        payment.assignTokens(tokenService.generateTokens(payment));
        payment.setStatus(PaymentStatus.COMPLETE);
        String paymentHash = DigestUtils.sha256Hex(paymentRequest);
        paymentSocketController.sendTokens(paymentHash.substring(0, 8), payment.getTokens());
    }

    public Page<Payment> getPaymentsByEmail(String email, Pageable pageable) {
        return paymentDataService.findAll(SearchableField.EMAIL, email, pageable);
    }
}
