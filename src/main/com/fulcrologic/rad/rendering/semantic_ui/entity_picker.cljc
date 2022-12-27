(ns com.fulcrologic.rad.rendering.semantic-ui.entity-picker
  (:require
    #?(:cljs [com.fulcrologic.fulcro.dom :as dom :refer [div h3 button i span]]
       :clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div h3 button i span]])
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro-i18n.i18n :refer [tr]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.options-util :refer [?!]]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.rendering.semantic-ui.form-options :as sufo]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [taoensso.timbre :as log]))

(declare CreationModal)

(defmutation entity-added [params]
  (action [{:keys [state ref]}]
    (log/info "ADDED")
    nil))

(defmutation start-create [{:keys [id ident form]}]
  (action [{:keys [app state]}]
    (swap! state update-in [:component/id ::CreationModal] assoc :ui/open? true :ui/form-props ident)
    (comp/set-query! app CreationModal {:query [:ui/open?
                                                {:ui/form-props (comp/get-query form @state)}]})
    (form/start-form! app
      id form
      {:embedded? true
       #_#_:on-saved [(entity-added {})]})))

(defsc CreationModal [this {:ui/keys [open? form-props] :as props} {:keys [onClose form]}]
  {:query         [:ui/open?
                   :ui/form-props]
   :ident         (fn [] [:component/id ::CreationModal])
   :initial-state {}}
  (ui-modal {:open open?}
    (ui-modal-header {} "")
    (ui-modal-content {}
      (when form
        (let [factory (comp/computed-factory form)]
          (when (and (seq form-props) (map? form-props))
            (factory form-props)))))
    (ui-modal-actions {}
      (dom/div :.ui.button "Cancel")
      (dom/div :.ui.button {:onClick (fn []
                                       (let [ident (comp/get-ident form form-props)]
                                         (when onClose
                                           (onClose ident))
                                         (m/set-value!! this :ui/open? false)))} "OK"))))

(def ui-creation-modal (comp/computed-factory CreationModal))

(defsc CreationContainer [this pass-through-props]
  {:use-hooks? true}
  (let [app   (comp/any->app this)
        props (hooks/use-component app CreationModal {:initialize?    true
                                                      :keep-existing? true})]
    (when props
      (ui-creation-modal props pass-through-props))))

(def ui-creation-container (comp/factory CreationContainer))

(defsc ToOnePicker [this {:keys [env attr]}]
  {:componentDidMount     (fn [this]
                            (let [{:keys [env attr]} (comp/props this)
                                  form-instance (::form/form-instance env)
                                  props         (comp/props form-instance)
                                  form-class    (comp/react-type form-instance)]
                              (po/load-options! form-instance form-class props attr)))
   :shouldComponentUpdate (fn [] true)}
  (let [{::form/keys [master-form form-instance]} env
        visible? (form/field-visible? form-instance attr)]
    (when visible?
      (let [{::form/keys [attributes field-options]} (comp/component-options form-instance)
            {::attr/keys [qualified-key required?]} attr
            field-options (get field-options qualified-key)
            target-id-key (first (keep (fn [{k ::attr/qualified-key ::attr/keys [target]}]
                                         (when (= k qualified-key) target)) attributes))
            {::po/keys [cache-key query-key creation-form]} (merge attr field-options)
            cache-key     (or (?! cache-key (comp/react-type form-instance) (comp/props form-instance)) query-key)
            cache-key     (or cache-key query-key (log/error "Ref field MUST have either a ::picker-options/cache-key or ::picker-options/query-key in attribute " qualified-key))
            props         (comp/props form-instance)
            options       (get-in props [::po/options-cache cache-key :options])
            value         [target-id-key (get-in props [qualified-key target-id-key])]
            field-label   (form/field-label env attr)
            read-only?    (or (form/read-only? master-form attr) (form/read-only? form-instance attr))
            invalid?      (and (not read-only?) (form/invalid-attribute-value? env attr))
            extra-props   (cond-> (?! (form/field-style-config env attr :input/props) env)
                            creation-form (merge {:allowAdditions   true
                                                  :additionPosition "top"
                                                  :onAddItem        #?(:clj  (fn [])
                                                                       :cljs (fn [_ ^js data]
                                                                               (let [id (tempid/tempid)]
                                                                                 (comp/transact! this [(start-create {:id            id
                                                                                                                      :initial-value (.-value data)
                                                                                                                      :form          creation-form
                                                                                                                      :ident         [target-id-key id]})]))))}))
            top-class     (sufo/top-class form-instance attr)
            onSelect      (fn [v] (form/input-changed! env qualified-key v))]
        (div {:className (or top-class "ui field")
              :classes   [(when invalid? "error")]}
          (when creation-form
            (ui-creation-container {:form    creation-form
                                    :onClose (fn [v]
                                               (log/info "Close " v)
                                               (when v
                                                 (let [{:keys [env attr]} (comp/props this)
                                                       form-instance (::form/form-instance env)
                                                       props         (comp/props form-instance)
                                                       form-class    (comp/react-type form-instance)]
                                                   (po/load-options! form-instance form-class props attr))
                                                 (onSelect v)))}))
          (dom/label field-label (when invalid? (str " (" (tr "Required") ")")))
          (if read-only?
            (let [value (first (filter #(= value (:value %)) options))]
              (:text value))
            (ui-wrapped-dropdown (merge extra-props
                                   {:onChange  (fn [v] (onSelect v))
                                    :value     value
                                    :clearable (not required?)
                                    :disabled  read-only?
                                    :options   options}))))))))

(let [ui-to-one-picker (comp/factory ToOnePicker {:keyfn (fn [{:keys [attr]}] (::attr/qualified-key attr))})]
  (defn to-one-picker [env attribute]
    (ui-to-one-picker {:env  env
                       :attr attribute})))

(defsc ToManyPicker [this {:keys [env attr]}]
  {:componentDidMount (fn [this]
                        (let [{:keys [env attr]} (comp/props this)
                              form-instance (::form/form-instance env)
                              props         (comp/props form-instance)
                              form-class    (comp/react-type form-instance)]
                          (po/load-options! form-instance form-class props attr)))}
  (let [{::form/keys [form-instance]} env
        visible? (form/field-visible? form-instance attr)]
    (when visible?
      (let [{::form/keys [attributes field-options]} (comp/component-options form-instance)
            {attr-field-options ::form/field-options
             ::attr/keys        [qualified-key]} attr
            field-options      (get field-options qualified-key)
            extra-props        (?! (form/field-style-config env attr :input/props) env)
            target-id-key      (first (keep (fn [{k ::attr/qualified-key ::attr/keys [target]}]
                                              (when (= k qualified-key) target)) attributes))
            {:keys     [style]
             ::po/keys [cache-key query-key]} (merge attr-field-options field-options)
            cache-key          (or (?! cache-key (comp/react-type form-instance) (comp/props form-instance)) query-key)
            cache-key          (or cache-key query-key (log/error "Ref field MUST have either a ::picker-options/cache-key or ::picker-options/query-key in attribute " qualified-key))
            props              (comp/props form-instance)
            options            (get-in props [::po/options-cache cache-key :options])
            current-selection  (into #{}
                                 (keep (fn [entity]
                                         (when-let [id (get entity target-id-key)]
                                           [target-id-key id])))
                                 (get props qualified-key))
            field-label        (form/field-label env attr)
            invalid?           (form/invalid-attribute-value? env attr)
            read-only?         (form/read-only? form-instance attr)
            top-class          (sufo/top-class form-instance attr)
            validation-message (when invalid? (form/validation-error-message env attr))]
        (div {:className (or top-class "ui field")
              :classes   [(when invalid? "error")]}
          (dom/label field-label " " (when invalid? validation-message))
          (div :.ui.middle.aligned.celled.list.big
            {:style {:marginTop "0"}}
            (if (= style :dropdown)
              (ui-wrapped-dropdown
                (merge extra-props
                  {:value    current-selection
                   :multiple true
                   :disabled read-only?
                   :options  options
                   :onChange (fn [v] (form/input-changed! env qualified-key v))}))
              (map (fn [{:keys [text value]}]
                     (let [checked? (contains? current-selection value)]
                       (div :.item {:key value}
                         (div :.content {}
                           (div :.ui.toggle.checkbox {:style {:marginTop "0"}}
                             (dom/input
                               (merge extra-props
                                 {:type     "checkbox"
                                  :checked  checked?
                                  :onChange #(if-not checked?
                                               (form/input-changed! env qualified-key (vec (conj current-selection value)))
                                               (form/input-changed! env qualified-key (vec (disj current-selection value))))}))
                             (dom/label text))))))
                options))))))))

(def ui-to-many-picker (comp/factory ToManyPicker {:keyfn :id}))
(let [ui-to-many-picker (comp/factory ToManyPicker {:keyfn (fn [{:keys [attr]}] (::attr/qualified-key attr))})]
  (defn to-many-picker [env attribute]
    (ui-to-many-picker {:env  env
                        :attr attribute})))
