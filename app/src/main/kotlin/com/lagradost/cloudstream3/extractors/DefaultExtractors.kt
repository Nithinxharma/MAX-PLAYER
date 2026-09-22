package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.ExtractorApi

object DefaultExtractors {
    fun registerAll() {
        val extractors: List<ExtractorApi> = listOf(
            // StreamWish and mirrors
            StreamWishExtractor(),
            Mwish(),
            Dwish(),
            Ewish(),
            Hgcloudto(),
            WishembedPro(),
            Wishfast(),
            Streamwish2(),
            SfastwishCom(),
            Strwish(),
            Strwish2(),
            FlaswishCom(),
            Awish(),
            Obeywish(),
            Jodwish(),
            Swhoi(),
            Multimovies(),
            UqloadsXyz(),
            Doodporn(),
            CdnwishCom(),
            Asnwish(),
            Nekowish(),
            Nekostream(),
            Swdyu(),
            Wishonly(),
            Playerwish(),
            StreamHLS(),
            HlsWish(),

            // FileMoon and mirrors
            FileMoon(),
            FileMoonIn(),
            FileMoonSx(),
            FilemoonV2(),

            // StreamTape and mirrors
            StreamTape(),
            StreamTapeNet(),
            StreamTapeXyz(),
            ShaveTape(),
            Watchadsontape(),

            // MixDrop and mirrors
            MixDrop(),
            MixDropPs(),
            Mdy(),
            MxDropTo(),
            MixDropSi(),
            MixDropBz(),
            MixDropAg(),
            MixDropCh(),
            MixDropTo(),

            // DoodStream and mirrors
            DoodLaExtractor(),
            Doodspro(),
            Dsvplay(),
            D0000d(),
            D000dCom(),
            DoodstreamCom(),
            Dooood(),
            DoodWfExtractor(),
            DoodCxExtractor(),
            DoodShExtractor(),
            DoodWatchExtractor(),
            DoodPmExtractor(),
            DoodToExtractor(),
            DoodSoExtractor(),
            DoodWsExtractor(),
            DoodYtExtractor(),
            DoodLiExtractor(),
            Ds2play(),
            Ds2video(),
            Vide0Net(),
            MyVidPlay(),
            Playmogo(),

            // RabbitStream / MegaCloud / DokiCloud / RapidCloud
            Rabbitstream(),
            Megacloud(),
            Dokicloud(),
            RapidCloud(),

            // VidSrc
            VidSrcExtractor(),
            VidSrcTo(),
            VidSrcMe(),
            VidSrcIn(),
            VidSrcPm(),
            VidSrcNet(),
            VidSrcXyz(),

            // Upstream & Mp4Upload
            UpstreamExtractor(),
            Mp4Upload(),

            // VidHide & FileLions
            VidhideExtractor(),
            VidHidePro(),
            Ryderjet(),
            VidHideHub(),
            VidHidePro1(),
            VidHidePro2(),
            VidHidePro3(),
            VidHidePro4(),
            VidHidePro5(),
            VidHidePro6(),
            Smoothpre(),
            Dhtpre(),
            Peytonepre(),

            // OkRu / Odnoklassniki
            OkRuSSL(),
            OkRuHTTP(),
            OkRuSSLMobile(),
            OkRuHTTPMobile(),
            Odnoklassniki(),

            // Voe and mirrors
            Voe(),
            Tubeless(),
            Simpulumlamerop(),
            Urochsunloath(),
            NathanFromSubject(),
            Yipsu(),
            MetaGnathTuggers(),
            Voe1(),
            Voe2(),

            // StreamSB and mirrors
            StreamSB(),
            Sblona(),
            Lvturbo(),
            Sbrapid(),
            Sbface(),
            Sbsonic(),
            Vidgomunimesb(),
            Sbasian(),
            Sbnet(),
            Keephealth(),
            Sbspeed(),
            Streamsss(),
            Sbflix(),
            Vidgomunime(),
            Sbthe(),
            Ssbstream(),
            SBfull(),
            StreamSB1(),
            StreamSB2(),
            StreamSB3(),
            StreamSB4(),
            StreamSB5(),
            StreamSB6(),
            StreamSB7(),
            StreamSB8(),
            StreamSB9(),
            StreamSB10(),
            StreamSB11(),
            Sblongvu(),

            // PixelDrain
            PixelDrain(),
            PixelDrainDev(),

            // Filesim, Jeniusplay, GMPlayer, Gdriveplayer
            Filesim(),
            Jeniusplay(),
            GMPlayer(),
            Gdriveplayer(),

            // Fastream, Streamlare, Streamhub, Vidmoly, StreamVid
            Fastream(),
            Streamlare(),
            Streamhub(),
            Streamhub2(),
            Vidmoly(),
            Vidmolyme(),
            Vidmolyto(),
            Vidmolybiz(),
            StreamVid(),

            // HubCloud family
            HubCloud(),
            HubuCloud(),

            // GDFlix family & mirrors
            GDFlix(),
            GDLink(),
            GDFlixApp(),
            GdFlix1(),
            GdFlix2(),
            GDFlixNet(),
            fastdlserver(),

            // Driveleech & DriveFire family
            Driveleech(),
            DriveleechNet(),
            DriveFire(),
            DriveFireIn(),

            // VCloud
            VCloud(),

            // Direct Media Hosts: Mediafire, Krakenfiles, Gofile, ByseSX, GDMirrorbot, VidStack
            Mediafire(),
            Krakenfiles(),
            Gofile(),
            ByseSX(),
            GDMirrorbot(),
            VidStack()
        )

        for (extractor in extractors) {
            APIHolder.addExtractor(extractor)
        }
    }
}
