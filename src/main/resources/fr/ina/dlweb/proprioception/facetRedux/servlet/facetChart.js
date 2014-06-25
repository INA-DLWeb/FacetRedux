function FacetChart(document, formId, formKeys, iframeKeys) {
    this.doc = document;
    this.formId = formId;
    this.iframeKeys = iframeKeys;
    this.formKeys = formKeys;
}

(function () {
    var NONE = '[NONE]';

    FacetChart.prototype = {
        'NONE': NONE,

        getFragment: function () {
            var u = this.doc.location.href;
            var i = u.indexOf('#');
            return u.substr(i > 0 ? i + 1 : 0);
        },

        getNoFragmentUrl: function () {
            var u = this.doc.location.href;
            var i = u.indexOf('#');
            return u.substr(0, i > 0 ? i : u.length);
        },

        readUrlFragment: function () {
            // read fragment
            var fragment = this.getFragment();

            // parse keyValue pairs
            var params = {};
            var kv,
                plus = /\+/g,  // Regex for replacing addition symbol with a space
                keyValueRE = /([^&;=]+)=?([^&;]*)/g,
                decode = function (s) {
                    return decodeURIComponent(s.replace(plus, " "));
                }
                ;
            while (kv = keyValueRE.exec(fragment))
                params[decode(kv[1])] = decode(kv[2]);

            // normalize params
            for (var k in this.formKeys) {
                var keyConf = this.formKeys[k];
                this.fixParam(params, k, keyConf['defaultValue'], keyConf['doEval']);
            }

            return params;
        },

        fixParam: function (params, name, defaultValue, doEval) {
            if (params[name] === undefined) {
                params[name] = defaultValue;
            } else if (params[name] === 'null') {
                params[name] = null;
            } else if (doEval) {
                try {
                    eval("params[name] = " + params[name] + ";");
                } catch (e) {
                    console.log("error while eval for '" + name + "' : " + e);
                    params[name] = undefined;
                }
            }
        },

        updateIframe: function (iframeId, formData, iframeData) {
            var d = this.doc;

            // set title
            var t = d.getElementsByTagName('title');
            if (t.length > 0) t[0].innerHTML = formData.desc;

            // update form url
            var formFragment = this.createFragment(formData, this.formKeys);
            var u = this.getNoFragmentUrl();
            this.doc.location.href = u + "#" + formFragment;

            // compute iframe url
            var iframeURL;
            if (iframeData.type == 'CSV') {
                iframeURL = iframeData.url + '&html=true';
            } else {
                var iframeBase = "/chart/index.html";
                var iframeFragment = this.createFragment(iframeData, this.iframeKeys);
                iframeURL = iframeBase + "#" + iframeFragment;
            }

            //console.log(chartURL);

            // update iframe url
            d.getElementById(iframeId).src = "./loading2.gif";
            setTimeout(function () {
                d.getElementById(iframeId).src = iframeURL;
            }, 500);
        },

        createFragment: function (data, keys) {
            var f = "";
            for (var k in keys) {
                var v = data[k];
                if (v !== undefined) {
                    f += "&" + k + "=" + (v === null ? 'null' : encodeURIComponent(v));
                }
            }
            f = f.length == 0 ? "" : f.substr(1);
            return f;
        },

        readForm: function () {
            var cf = this.doc.getElementById(this.formId), formData = {};
            for (var k in this.formKeys) {
                var v;
                if (cf[k].type == 'checkbox') {
                    v = cf[k].checked;
                } else {
                    v = cf[k].value;
                }
                formData[k] = (v === NONE) ? null : v;
            }
            return formData;
        },

        updateForm: function (formData) {
            console.log("updateForm(formData) : " + JSON.stringify(formData, undefined, ' '));

            var cf = this.doc.getElementById(this.formId);
            for (var k in this.formKeys) {
                // init. the field options
                var c = this.formKeys[k];
                if (c['options'] !== undefined) {
                    var o = c.options;
                    var ff = '';
                    for (var i = 0, l = o.length; i < l; ++i) {
                        if (o[i] === undefined) ff += '<option>' + NONE + '</option>';
                        else ff += '<option>' + o[i] + '</option>';
                    }
                    cf[k].innerHTML = ff;
                }

                // update the form field's value
                var v = formData[k];
                if (v !== undefined) {
                    if (cf[k].type == 'checkbox') {
                        cf[k].checked = v;
                    } else {
                        cf[k].value = v;
                    }
                } else if (c['options'] !== undefined) {
                    cf[k].value = NONE;
                }
            }
        }
    };
}());
