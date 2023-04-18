(ns com.fulcrologic.rad.rendering.semantic-ui.enumerated-field
  (:require
    #?@(:cljs
        [[com.fulcrologic.fulcro.dom :as dom :refer [div label input]]
         [cljs.reader :refer [read-string]]
         [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]]
        :clj
        [[com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]])
    [com.fulcrologic.fulcro-i18n.i18n :refer [tr]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.options-util :refer [?!]]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.rendering.semantic-ui.form-options :as sufo]
    [clojure.string :as str]
    [com.fulcrologic.rad.form :as form]

    [se.w3t.flowbite.factories :as f]))

(defn enumerated-options [{::form/keys [form-instance] :as env} {::attr/keys [qualified-key] :as attribute}]
  (let [{::attr/keys [enumerated-values]} attribute
        enumeration-labels (merge
                             (::attr/enumerated-labels attribute)
                             (comp/component-options form-instance ::form/enumerated-labels qualified-key))]
    ;; TODO: Sorting should be something users control
    (sort-by :text
      (mapv (fn [k]
              {:text  (?! (get enumeration-labels k (name k)))
               :value k}) enumerated-values))))

(defn- render-to-many [{::form/keys [form-instance] :as env} {::attr/keys [qualified-key computed-options] :as attribute}]
  (when (form/field-visible? form-instance attribute)
    (let [props        (comp/props form-instance)
          read-only?   (form/read-only? form-instance attribute)
          options      (or (?! computed-options env) (enumerated-options env attribute))
          top-class    (sufo/top-class form-instance attribute)
          selected-ids (set (get props qualified-key))]
      (div {:className (or top-class "ui field")
            :key       (str qualified-key)}
        (label (form/field-label env attribute))
        (div {:class "flex flex-col gap-2 mt-2"}
          (map (fn [{:keys [text value]}]
                 (let [checked? (contains? selected-ids value)]
                   (f/ui-toggle-switch
                    {:checked  checked?
                     :disabled read-only?
                     :onChange #(let [selection (if-not checked?
                                                  (conj (set (or selected-ids #{})) value)
                                                  (disj selected-ids value))]
                                  (form/input-changed! env qualified-key selection))
                     :label text})))
            options))))))

(defn- render-to-one [{::form/keys [form-instance] :as env} {::attr/keys [qualified-key computed-options required?] :as attribute}]
  (when (form/field-visible? form-instance attribute)
    (let [props      (comp/props form-instance)
          read-only? (form/read-only? form-instance attribute)
          invalid?   (form/invalid-attribute-value? env attribute)
          user-props (?! (form/field-style-config env attribute :input/props) env)
          options    (or (?! computed-options env) (enumerated-options env attribute))
          top-class  (sufo/top-class form-instance attribute)
          value      (get props qualified-key)]
      (div {:className (or top-class "ui field")
            :key       (str qualified-key) :classes [(when invalid? "error")]}
        (label {:class "text-gray-900 text-sm py-3 dark:text-white"} (str (form/field-label env attribute)
                      (when invalid? (str " (" (tr "Required") ")"))))
        (if read-only?
          (let [value (first (filter #(= value (:value %)) options))]
            (dom/input {:readOnly "readonly"
                        :value    (:text value)}))
          (f/ui-select (merge
                        {:options   options
                         :required required?
                         :class "border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-zinc-800 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500"
                                        ;:value     value
                         :onChange  (fn [v] (let [v (cljs.reader/read-string v.nativeEvent.target.value)]
                                              (form/input-changed! env qualified-key v)))}
                        user-props)
                       (for [o options]
                         (dom/option {:value (str (:value o))} (:text o)))))))))

(defn render-field [env {::attr/keys [cardinality] :or {cardinality :one} :as attribute}]
  (if (= :many cardinality)
    (render-to-many env attribute)
    (render-to-one env attribute)))
