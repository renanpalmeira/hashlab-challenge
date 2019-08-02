(ns com.hash.product.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [com.hash.product.client.discount :as discount-client]
            [com.hash.product.db :as db]
            [com.hash.product.util :as util])
  (:import (com.hash.proto DiscountServiceGrpc)))

(def db {:connection-uri "jdbc:postgresql://localhost:5432/hashlab?user=hashlab&password=hashlab"})

(def discount-client
  (DiscountServiceGrpc/newBlockingStub (util/create-channel "localhost" 50051)))

(defn product->product-with-discount
  [product user-id]
  (let [discount-payload {:product-id (str (:id product))
                          :user-id    user-id}
        discount (discount-client/get-discount discount-client discount-payload)]
    (prn discount-payload)
    (assoc product :discount discount)))

(defn get-products
  [{:keys [headers path-info]}]
  (let [products (db/get-products db)
        user-id (get headers "x-user-id" "0")]
    (if products
      (ring-resp/response
        (util/resources (map #(product->product-with-discount % user-id) products)))
      (ring-resp/not-found
        (util/error "ResourcesNotFoundError" path-info "Products not found")))))

(defn get-product
  [{{:keys [product-id]} :path-params
    :keys [headers path-info]}]

  (let [product (db/get-product db product-id)
        user-id (get headers "x-user-id" "0")]
    (if product
      (ring-resp/response
        (product->product-with-discount product user-id))
      (ring-resp/not-found
        (util/error "ResourceNotFoundError" path-info "Product id not found")))))

(def common-interceptors
  [(body-params/body-params)
   http/json-body])

;; Tabular routes
(def routes #{["/product" :get (conj common-interceptors `get-products)]
              ["/product/:product-id" :get (conj common-interceptors `get-product)]})

;; Consumed by product-service.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"

              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ::http/container-options {:h2c? true
                                        :h2? false
                                        :ssl? false}})
