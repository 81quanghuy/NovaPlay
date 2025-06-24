package vn.iotstar.authservice.model.mapper;

import vn.iotstar.authservice.model.dto.AccountDTO;
import vn.iotstar.authservice.model.entity.Account;

public class AccountMapper {

    /**
     * Converts an Account entity to an AccountDTO.
     *
     * @param pAccount the Account entity to convert
     * @return the converted AccountDTO, or null if pAccount is null
     */
    public static AccountDTO toAccountDTO(Account pAccount) {
        if (pAccount == null) {
            return null;
        }
        AccountDTO accountDTO = new AccountDTO();
        if (pAccount.getId() != null) {
            accountDTO.setAccountId(pAccount.getId());
        }
        accountDTO.setEmail(pAccount.getEmail());
        accountDTO.setPassword(pAccount.getPassword());
        return accountDTO;
    }

    /**
     * Converts an AccountDTO to an Account entity.
     *
     * @param pAccountDTO the AccountDTO to convert
     * @return the converted Account entity, or null if pAccountDTO is null
     */
    public static Account toAccount(AccountDTO pAccountDTO) {
        if (pAccountDTO == null) {
            return null;
        }
        Account account = new Account();
        account.setId(pAccountDTO.getAccountId());
        account.setEmail(pAccountDTO.getEmail());
        account.setPassword(pAccountDTO.getPassword());
        return account;
    }
}
