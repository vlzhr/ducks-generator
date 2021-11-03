package farming;

import com.wavesplatform.transactions.common.Amount;
import env.TestEnvironment;
import models.Duck;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static models.Farming.*;
import static models.Referral.*;
import static im.mak.paddle.assertj.PaddleAssertions.assertThat;
import static im.mak.paddle.assertj.PaddleAssertions.assertThrows;
import static im.mak.paddle.token.Waves.WAVES;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class BuyPerchTest extends TestEnvironment {

    @Test
    void cannotBuyPerchIfNotInit() {
        getEggsFromFaucet(ALICE, EGG.of(1));
        var randomColor = getRandomColor();

        assertThrows(() -> ALICE.invoke(FARMING.buyPerch(randomColor, ""), EGG.of(1)))
                .hasMessageEndingWith("not init");
    }

    static Stream<String> canBuyPerchForAnyColor() {
        return COLORS.stream();
    }

    @ParameterizedTest
    @MethodSource
    void canBuyPerchForAnyColor(String color) {
        FARMING.invoke(FARMING.init());
        getEggsFromFaucet(ALICE, EGG.of(10));

        var invokeInfo =
                ALICE.invoke(FARMING.buyPerch(color, ""), EGG.of(1));

        assertThat(invokeInfo.stateChanges()).containsExactly(sc -> sc
                .integerEntry(keyPerchesAvailable(ALICE, color), 1)
                .invoke(REFERRAL.refPayment(""), EGG.of(0.05)));
        assertThat(REFERRAL)
                .hasBalance(WAVES.of(0.99))
                .hasAssetsExactly(EGG.of(0.05));
        assertThat(ALICE).hasAssetsExactly(EGG.of(9));
    }

    @TestFactory
    Stream<DynamicTest> cannotBuyPerchForInvalidColor() {
        FARMING.invoke(FARMING.init());
        getEggsFromFaucet(ALICE, EGG.of(100));

        return Stream.of(("BLUE,BB,b,u,A,C,D,E,F,H,I,J,K,L,M,N,O,P,Q,S,T,V,W,X,Z," + JACKPOT_COLOR).split(","))
                .map(color -> dynamicTest(color, () ->
                        assertThrows(() -> ALICE.invoke(FARMING.buyPerch(color, ""), EGG.of(1)))
                                .hasMessageEndingWith("you need to set color properly")));
    }

    @TestFactory
    Stream<DynamicTest> cannotBuyPerchForDifferentPrice() {
        FARMING.invoke(FARMING.init());
        getEggsFromFaucet(ALICE, EGG.of(10_000));

        var randomColor = getRandomColor();

        return Stream.of(1L, PERCH_PRICE - 1, PERCH_PRICE + 1).map(price ->
                dynamicTest(price + " of EGG tokens", () ->
                        assertThrows(() ->
                                ALICE.invoke(FARMING.buyPerch(randomColor, ""), Amount.of(price, EGG.id()))
                        ).hasMessageEndingWith("To buy a perch you currently need the following amount of EGGlets: " + EGG.amount(1))));
    }

    @TestFactory
    Stream<DynamicTest> cannotBuyPerchForDifferentAsset() {
        FARMING.invoke(FARMING.init());
        getEggsFromFaucet(ALICE, EGG.of(10_000));

        var randomColor = getRandomColor();

        Duck duck = hatchDuck(ALICE);
        Amount fakeDuck = Amount.of(1, ALICE.issueNft(a -> a.name("DUCK-AAAAAAAA-GB")).tx().assetId());

        return Stream.of(WAVES.of(0.000001), duck.of(1), fakeDuck).map(payment ->
                dynamicTest(payment.toString(), () -> assertThrows(() ->
                        ALICE.invoke(FARMING.buyPerch(randomColor, ""), payment)
                ).hasMessageEndingWith("You can attach only EGG tokens with the following asset id: " + EGG.id())));
    }

    @Test
    void referrerReceivesRewardIfSpecified() {
        FARMING.invoke(FARMING.init());
        getEggsFromFaucet(ALICE, EGG.of(10_000));

        var randomColor = getRandomColor();

        var invokeInfo =
                ALICE.invoke(FARMING.buyPerch(randomColor, BOBBY.address()), EGG.of(1));

        assertThat(invokeInfo.stateChanges()).containsExactly(sc -> sc
                .integerEntry(keyPerchesAvailable(ALICE, randomColor), 1)
                .invoke(REFERRAL.refPayment(BOBBY), EGG.of(0.05), expectedChanges -> expectedChanges
                        .stringEntry(keyReferredBy(ALICE), BOBBY.address().toString())
                        .integerEntry(keyEarnedReward(BOBBY), EGG.amount(0.05))
                        .integerEntry(keyDeliveredReward(BOBBY, ALICE), EGG.amount(0.05))
                        .integerEntry(keyAmount(BOBBY, ALICE), 1)
                        .transfer(BOBBY, EGG.of(0.05))
                ));
    }

    @Test
    void userCannotBeReferrerForItself() {
        FARMING.invoke(FARMING.init());
        getEggsFromFaucet(ALICE, EGG.of(10_000));

        var randomColor = getRandomColor();

        var invokeInfo =
                ALICE.invoke(FARMING.buyPerch(randomColor, ALICE.address()), EGG.of(1));

        assertThat(invokeInfo.stateChanges()).containsExactly(sc -> sc
                .integerEntry(keyPerchesAvailable(ALICE, randomColor), 1)
                .invoke(REFERRAL.refPayment(ALICE), EGG.of(0.05)));
    }

}
