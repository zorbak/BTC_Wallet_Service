## Running the code on Local

From Author: Web Service related work is not my expert / experienced area. I was working at Agoda as a Software Engineer which maining focused on the operation aspect of the website. To be more specific, the back-end logic part of the system. So this take-home-test is quite new to me. So i did not develope the project from scratch, i have to start from the shopping-cart code provided by the AKKA tutorial and used them as a starting point of the project then refactor, modify and develope from that to be able to satisfy the requirement. The logic is totally different but the AKKA and web service technique remains the same such as Event Sourcing. As a result, some variables and relevant config file names can not be changed due to some fixed dependency.

I decided to use postgresql as a DB because i worked locally. To scale, we can use Apache Hadoop system to store the table as my previous comany used to do which is a good way to scale.

1. Start Docker

    ```shell
    docker-compose up -d

    # creates the tables needed for Akka Persistence
    # as well as the offset store table for Akka Projection
    docker exec -i shopping-cart-service_postgres-db_1 psql -U shopping-cart -t < ddl-scripts/create_tables.sql
    
    # creates the user defined projection table.
    docker exec -i shopping-cart-service_postgres-db_1 psql -U shopping-cart -t < ddl-scripts/create_transaction_table.sql
    ```

2. Start a first node:

    ```
    sbt -Dconfig.resource=local1.conf run
    ```

3. Try it with grpcurl

    ```shell
    # add transaction to the wallet
    grpcurl -plaintext -d "{\"walletId\":\"wallet1\", \"datetime\":\"2019-10-05T14:48:03+01:00\", \"amount\":1.2}" 127.0.0.1:8101 shoppingcart.WalletService.AddTransaction
    grpcurl -plaintext -d "{\"walletId\":\"wallet1\", \"datetime\":\"2019-10-05T15:00:03+01:00\", \"amount\":50}" 127.0.0.1:8101 shoppingcart.WalletService.AddTransaction
    grpcurl -plaintext -d "{\"walletId\":\"wallet1\", \"datetime\":\"2019-10-05T17:00:03+02:00\", \"amount\":30}" 127.0.0.1:8101 shoppingcart.WalletService.AddTransaction
    grpcurl -plaintext -d "{\"walletId\":\"wallet1\", \"datetime\":\"2019-10-05T19:00:15+01:00\", \"amount\":40}" 127.0.0.1:8101 shoppingcart.WalletService.AddTransaction
    grpcurl -plaintext -d "{\"walletId\":\"wallet1\", \"datetime\":\"2019-10-06T13:48:03+01:00\", \"amount\":70}" 127.0.0.1:8101 shoppingcart.WalletService.AddTransaction
   
    # get the balance with startTime and endTime
    grpcurl -plaintext -d "{\"starttime\":\"2019-10-05T14:48:05+01:00\", \"endtime\":\"2019-10-05T19:48:02+01:00\"}" 127.0.0.1:8101 shoppingcart.WalletService.GetWalletBalance
    ```
