package com.gakkum.backend.global.transaction;

import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 없이 Facade를 조립하는 테스트용. 트랜잭션을 열지 않고 콜백만 바로 실행한다. */
public class ImmediateTransactionTemplate extends TransactionTemplate {

    @Override
    public <T> T execute(TransactionCallback<T> action) {
        return action.doInTransaction(new SimpleTransactionStatus());
    }
}
