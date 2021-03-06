package org.wikipedia.wikidata;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.google.gson.JsonParseException;

import org.wikipedia.dataclient.WikiSite;
import org.wikipedia.dataclient.retrofit.RetrofitFactory;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

public final class EntityClient {
    public static final String WIKIDATA_WIKI = "wikidatawiki";
    @NonNull private final Service service;

    public interface LabelCallback {
        void success(@NonNull String label);
        void failure(Throwable t);
    }

    private static final EntityClient INSTANCE = new EntityClient();

    public static EntityClient instance() {
        return INSTANCE;
    }

    private EntityClient() {
        service = RetrofitFactory.newInstance(new WikiSite("www.wikidata.org", ""))
                .create(Service.class);
    }

    @VisibleForTesting
    static class LabelCallbackAdapter implements retrofit2.Callback<Entity.EntitiesResponse> {
        @NonNull private final LabelCallback callback;
        @NonNull private final String qNumber;
        @NonNull private final String langCode;

        LabelCallbackAdapter(@NonNull LabelCallback callback, @NonNull final String qNumber,
                             @NonNull final String langCode) {
            this.callback = callback;
            this.qNumber = qNumber;
            this.langCode = langCode;
        }

        @Override public void onResponse(Call<Entity.EntitiesResponse> call,
                                         Response<Entity.EntitiesResponse> response) {
            if (response.body() != null && response.body().success()) {
                for (Entity item : response.body().entities().values()) {
                    if (item.id().equals(qNumber)) {
                        for (Entity.Label label : item.labels().values()) {
                            if (label.language().equals(langCode)) {
                                callback.success(label.value());
                                return;
                            }
                        }
                    }
                }
            }
            callback.failure(new JsonParseException("Failed to find label for " + qNumber + ":" + langCode));
        }

        @Override public void onFailure(Call<Entity.EntitiesResponse> call, Throwable caught) {
            callback.failure(caught);
        }
    }

    public void getLabelForLang(@NonNull final String qNumber, @NonNull final String langCode,
                                @NonNull final LabelCallback callback) {
        requestLabels(service, qNumber, langCode)
                .enqueue(new LabelCallbackAdapter(callback, qNumber, langCode));
    }

    @VisibleForTesting
    @NonNull
    Call<Entity.EntitiesResponse> requestLabels(@NonNull Service service,
                                                @NonNull final String qNumber,
                                                @NonNull final String langCode) {
        return service.getLabels(qNumber, langCode);
    }

    @VisibleForTesting
    interface Service {
        String ACTION = "w/api.php?action=wbgetentities&format=json";

        @GET(ACTION + "&props=labels&languagefallback=1")
        @NonNull Call<Entity.EntitiesResponse> getLabels(@Query("ids") @NonNull String idList,
                                                         @Query("languages") @NonNull String langList);
    }
}
