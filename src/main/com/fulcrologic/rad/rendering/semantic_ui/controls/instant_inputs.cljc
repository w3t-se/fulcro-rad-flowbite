(ns com.fulcrologic.rad.rendering.semantic-ui.controls.instant-inputs
  (:require
   [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.guardrails.core :refer [>defn => ?]]
    [com.fulcrologic.rad.type-support.date-time :as dt]
    [com.fulcrologic.rad.rendering.semantic-ui.controls.control :as control]
    [cljc.java-time.local-time :as lt]
    [com.fulcrologic.fulcro.dom.events :as evt]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom]
       :cljs [com.fulcrologic.fulcro.dom :as dom])
    [cljc.java-time.local-date-time :as ldt]
    [cljc.java-time.local-date :as ld]
    [applied-science.js-interop :as j]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.react.hooks :refer [use-state]]
    #?(:cljs ["tailwind-datepicker-react" :default Datepicker])))

#?(:cljs (do
           (def ui-datepicker (interop/react-factory Datepicker))

           (comp/defsc DateInput [this props]
             {:use-hooks? true}
             (let [[show setShow] (use-state false)
                   [selectedDate setSelectedDate] (use-state (:value props))]
               (dom/div {}
                 (ui-datepicker (merge props
                                       {:options {:todayBtn true :clearBtn true :language "en"
                                                  :defaultDate (:value props)
                                                  :theme {}
                                                  :autoHide true}
                                        :show show
                                        ;:value 
                                        :setShow setShow
                                        :onChange (fn [e]
                                                    ((:onChange props) e #_(some-> e
                                                                               (dt/html-date-string->local-date)
                                                                               (dt/local-date->inst))))})))))

           (def ui-date-input (comp/computed-factory DateInput))))

(defn ui-date-instant-input [{:keys [value onChange local-time] :as props}]
  (let [value      (if (nil? value) "" (dt/inst->html-date value))
        local-time (or local-time "")]
    #?(:cljs
       (ui-date-input (merge
                       props
                       {:onChange onChange})))))

(defn ui-ending-date-instant-input
  "Display the date the user selects, but control a value that is midnight on the next date. Used for generating ending
  instants that can be used for a proper non-inclusive end date."
  [_ {:keys [value onChange] :as props}]
  (let [value        (if (nil? value)
                       ""
                       (-> value
                           dt/inst->local-datetime
                           (ldt/minus-days 1)
                           ldt/to-local-date
                           dt/local-date->html-date-string))]
    (ui-date-input
      (merge props
        {:value    value
         :todayBtn true :clearBtn true :language "en"
         :show true
         :onChange (fn [evt]
                     (when onChange
                       (onChange (some-> (evt/target-value evt)
                                         (dt/html-date-string->local-date)
                                         (ld/plus-days 1)
                                         (ld/at-time lt/midnight)
                                         (dt/local-datetime->inst)))))}))))

(defn ui-date-time-instant-input [_ {:keys [disabled? value onChange] :as props}]
  (let [value (if (nil? value) "" (dt/inst->html-datetime-string value))]
    (ui-date-input
      (merge props
        (cond->
          {:value    value
           :todayBtn true :clearBtn true :language "en"
           :onChange (fn [evt]
                       (when onChange
                         (let [date-time-string (evt/target-value evt)
                               instant          (dt/html-datetime-string->inst date-time-string)]
                           (onChange instant))))}
          disabled? (assoc :readOnly true))))))

(defn date-time-control [render-env]
  (control/ui-control (assoc render-env :input-factory ui-date-time-instant-input)))

(defn midnight-on-date-control [render-env]
  (control/ui-control (assoc render-env
                        :input-factory ui-date-instant-input
                        ::default-local-time lt/midnight)))

(defn midnight-next-date-control [render-env]
  (control/ui-control (assoc render-env
                        :input-factory ui-ending-date-instant-input)))

(defn date-at-noon-control [render-env]
  (control/ui-control (assoc render-env
                             ::default-local-time lt/noon
                             :input-factory ui-date-instant-input)))

