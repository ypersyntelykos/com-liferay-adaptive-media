/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.adaptive.media.image.item.selector.internal.resolver;

import com.liferay.adaptive.media.AdaptiveMedia;
import com.liferay.adaptive.media.image.finder.AdaptiveMediaImageFinder;
import com.liferay.adaptive.media.image.item.selector.AdaptiveMediaImageURLItemSelectorReturnType;
import com.liferay.adaptive.media.image.processor.AdaptiveMediaImageAttribute;
import com.liferay.adaptive.media.image.processor.AdaptiveMediaImageProcessor;
import com.liferay.document.library.kernel.util.DLUtil;
import com.liferay.item.selector.ItemSelectorReturnTypeResolver;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Roberto Díaz
 */
@Component(
	immediate = true, property = {"service.ranking:Integer=100"},
	service = {
		ItemSelectorReturnTypeResolver.class,
		FileEntryAdaptiveMediaImageURLItemSelectorReturnTypeResolver.class
	}
)
public class FileEntryAdaptiveMediaImageURLItemSelectorReturnTypeResolver
	implements ItemSelectorReturnTypeResolver
		<AdaptiveMediaImageURLItemSelectorReturnType, FileEntry> {

	@Override
	public Class<AdaptiveMediaImageURLItemSelectorReturnType>
		getItemSelectorReturnTypeClass() {

		return AdaptiveMediaImageURLItemSelectorReturnType.class;
	}

	@Override
	public Class<FileEntry> getModelClass() {
		return FileEntry.class;
	}

	@Override
	public String getValue(FileEntry fileEntry, ThemeDisplay themeDisplay)
		throws Exception {

		JSONObject fileEntryJSONObject = JSONFactoryUtil.createJSONObject();

		String previewURL = DLUtil.getPreviewURL(
			fileEntry, fileEntry.getFileVersion(), themeDisplay,
			StringPool.BLANK, false, false);

		fileEntryJSONObject.put("defaultSource", previewURL);

		JSONArray sourcesArray = JSONFactoryUtil.createJSONArray();

		Stream<AdaptiveMedia<AdaptiveMediaImageProcessor>> stream =
			_finder.getAdaptiveMedia(
				queryBuilder ->
					queryBuilder.allForFileEntry(fileEntry).orderBy(
						AdaptiveMediaImageAttribute.IMAGE_WIDTH, true).done());

		List<AdaptiveMedia<AdaptiveMediaImageProcessor>> adaptiveMedias =
			stream.collect(Collectors.toList());

		AdaptiveMedia previousAdaptiveMedia = null;

		for (AdaptiveMedia<AdaptiveMediaImageProcessor> adaptiveMedia :
				adaptiveMedias) {

			Optional<AdaptiveMedia<AdaptiveMediaImageProcessor>>
				hdAdaptiveMediaOptional = _getHDAdaptiveMedia(
					adaptiveMedia, adaptiveMedias);

			JSONObject sourceJSONObject = _getSourceJSONObject(
				adaptiveMedia, previousAdaptiveMedia, hdAdaptiveMediaOptional);

			sourcesArray.put(sourceJSONObject);

			previousAdaptiveMedia = adaptiveMedia;
		}

		fileEntryJSONObject.put("sources", sourcesArray);

		return fileEntryJSONObject.toString();
	}

	private Optional<AdaptiveMedia<AdaptiveMediaImageProcessor>>
		_getHDAdaptiveMedia(
			AdaptiveMedia<AdaptiveMediaImageProcessor> originalAdaptiveMedia,
			List<AdaptiveMedia<AdaptiveMediaImageProcessor>> adaptiveMedias) {

		Optional<Integer> originalWidthOptional =
			originalAdaptiveMedia.getAttributeValue(
				AdaptiveMediaImageAttribute.IMAGE_WIDTH);

		Optional<Integer> originalHeightOptional =
			originalAdaptiveMedia.getAttributeValue(
				AdaptiveMediaImageAttribute.IMAGE_HEIGHT);

		if (!originalWidthOptional.isPresent() ||
			!originalHeightOptional.isPresent()) {

			return Optional.empty();
		}

		for (AdaptiveMedia<AdaptiveMediaImageProcessor> adaptiveMedia :
				adaptiveMedias) {

			Optional<Integer> widthOptional = adaptiveMedia.getAttributeValue(
				AdaptiveMediaImageAttribute.IMAGE_WIDTH);

			Optional<Integer> heightOptional = adaptiveMedia.getAttributeValue(
				AdaptiveMediaImageAttribute.IMAGE_HEIGHT);

			if (!widthOptional.isPresent() || !heightOptional.isPresent()) {
				continue;
			}

			int originalWidth = originalWidthOptional.get() * 2;
			int originalHeight = originalHeightOptional.get() * 2;

			boolean widthMatch = IntStream.range(
				originalWidth - 1, originalWidth + 2).anyMatch(
					value -> value == widthOptional.get());
			boolean heightMatch = IntStream.range(
				originalHeight - 1, originalHeight + 2).anyMatch(
					value -> value == heightOptional.get());

			if (widthMatch && heightMatch) {
				return Optional.of(adaptiveMedia);
			}
		}

		return Optional.empty();
	}

	private JSONObject _getSourceJSONObject(
		AdaptiveMedia<AdaptiveMediaImageProcessor> adaptiveMedia,
		AdaptiveMedia<AdaptiveMediaImageProcessor> previousAdaptiveMedia,
		Optional<AdaptiveMedia<AdaptiveMediaImageProcessor>>
			hdAdaptiveMediaOptional) {

		Optional<Integer> widthOptional = adaptiveMedia.getAttributeValue(
			AdaptiveMediaImageAttribute.IMAGE_WIDTH);

		JSONObject sourceJSONObject = JSONFactoryUtil.createJSONObject();

		StringBundler sb = new StringBundler(4);

		sb.append(adaptiveMedia.getURI());

		hdAdaptiveMediaOptional.ifPresent(
			hdAdaptiveMedia -> {
				sb.append(", ");
				sb.append(hdAdaptiveMedia.getURI());
				sb.append(" 2x");
			});

		sourceJSONObject.put("src", sb.toString());

		JSONObject attributesJSONObject = JSONFactoryUtil.createJSONObject();

		widthOptional.ifPresent(
			maxWidth -> {
				attributesJSONObject.put("max-width", maxWidth + "px");

				if (previousAdaptiveMedia != null) {
					Optional<Integer> previousWidthOptional =
						previousAdaptiveMedia.getAttributeValue(
							AdaptiveMediaImageAttribute.IMAGE_WIDTH);

					previousWidthOptional.ifPresent(
						previousMaxWidth ->
							attributesJSONObject.put(
								"min-width", previousMaxWidth + "px"));
				}
			});

		return sourceJSONObject.put("attributes", attributesJSONObject);
	}

	@Reference(
		target = "(model.class.name=com.liferay.portal.kernel.repository.model.FileVersion)"
	)
	private AdaptiveMediaImageFinder _finder;

}