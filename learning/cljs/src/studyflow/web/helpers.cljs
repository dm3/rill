(ns studyflow.web.helpers
  (:require [om.dom :as dom]
            [goog.string :as gstring]
            [clojure.string :as string]))

(defn raw-html
  [raw]
  (dom/span #js {:dangerouslySetInnerHTML #js {:__html raw}} nil))

(defn update-js [js-obj key f]
  (let [key (if (keyword? key)
              (name key)
              key)
        p (.-props js-obj)]
    (aset p key (f (get p key)))
    js-obj))

(defn modal [content primary-button & [secondary-button]]
  (dom/div #js {:id "m-modal"
                :className "show"}
           (dom/div #js {:className "modal_inner"}
                    content
                    (dom/div #js {:className "modal_footer"}
                             (when secondary-button
                               (update-js secondary-button
                                          :className (fnil (partial str "btn big gray") "")))
                             (update-js primary-button
                                        :className (partial str "btn big yellow pull-right"))))))

(def html->om
  {"a" dom/a, "b" dom/b, "big" dom/big, "br" dom/br, "dd" dom/dd, "div" dom/div,
   "dl" dom/dl, "dt" dom/dt, "em" dom/em, "fieldset" dom/fieldset,
   "h1" dom/h1, "h2" dom/h2, "h3" dom/h3, "h4" dom/h4, "h5" dom/h5, "h6" dom/h6,
   "hr" dom/hr, "i" dom/i, "li" dom/li, "ol" dom/ol, "p" dom/p, "pre" dom/pre,
   "q" dom/q,"s" dom/s,"small" dom/small, "span" dom/span, "strong" dom/strong,
   "sub" dom/sub, "sup" dom/sup, "table" dom/table, "tbody" dom/tbody, "td" dom/td,
   "tfoot" dom/tfoor, "th" dom/th, "thead" dom/thead, "tr" dom/tr, "u" dom/u,
   "ul" dom/ul})

(defn tag-tree-to-om [tag-tree inputs]
  (let [descent (fn descent [tag-tree]
                  (cond
                   (and (map? tag-tree)
                        (contains? tag-tree :tag)
                        (contains? tag-tree :attrs)
                        (contains? tag-tree :content))
                   (let [{:keys [tag attrs content] :as node} tag-tree]
                     (if-let [build-fn (get html->om tag)]
                       (apply build-fn
                              (if-let [className (:class attrs)]
                                #js {:className className}
                                nil)
                              (map descent content))
                       (cond
                        (= tag "img")
                        (dom/img #js {:src (:src attrs)})
                        (= tag "input")
                        (get inputs (:name attrs))
                        (= tag "svg")
                        (raw-html content)
                        :else
                        (apply dom/span #js {:className "default-html-to-om"}
                               (map descent content)))))
                   (string? tag-tree)
                   tag-tree))]
    (descent tag-tree)))
