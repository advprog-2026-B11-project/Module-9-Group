package com.example.bidmart.bidding.validator;

import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.service.ListingSnapshot;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the rules of a bid attempt.
 *
 * <p>Split into two phases so the service can fail fast on malformed input
 * before performing any I/O, then enforce business rules once the necessary
 * context (listing, current highest bid) has been resolved.
 */
public interface BidRuleValidator {

    /**
     * Phase 1 – pure input validation; no I/O required.
     *
     * <p>Checks that the request is structurally sound and that all required
     * fields are present and logically consistent (e.g. proxyMaxLimit ≥ amount).
     *
     * @param request  the incoming bid request
     * @param buyerId  the authenticated buyer placing the bid
     * @throws com.example.bidmart.bidding.exception.BidValidationException on any violation
     */
    void validateRequest(CreateBidRequest request, UUID buyerId);

    /**
     * Phase 2 – contextual business-rule validation; called after the listing
     * and the current highest bid have been loaded.
     *
     * <p>Enforces rules such as:
     * <ul>
     *   <li>The buyer must not be the seller of the listing.</li>
     *   <li>The auction window must still be open.</li>
     *   <li>The bid must meet or exceed the starting price.</li>
     *   <li>The bid must beat the current highest bid.</li>
     * </ul>
     *
     * @param buyerId            the authenticated buyer placing the bid
     * @param listing            a snapshot of the target listing
     * @param bidAmount          the amount proposed by the buyer
     * @param currentHighestBid  the highest bid on record, if any
     * @throws com.example.bidmart.bidding.exception.BidValidationException on any violation
     */
    void validateBidContext(
            UUID buyerId,
            ListingSnapshot listing,
            BigDecimal bidAmount,
            Optional<Bid> currentHighestBid
    );
}
